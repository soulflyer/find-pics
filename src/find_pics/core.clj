(ns find-pics.core
  (:import java.io.File)
  (:require [image-lib.core :refer [find-images
                                    image-path
                                    get-best-image]]
            [clojure.string :refer [join]]
            [clojure.java.shell :refer [sh]]
            [monger
             [collection :as mc]
             [core :as mg]]
            [seesaw
             [core :refer :all]
             [tree :refer :all]
             [keymap :refer :all]])
  (:gen-class)
  )

(def database "soulflyer")
(def keyword-collection "keywords")
(def preferences-collection "preferences")
(def images-collection "images")
(def connection (mg/connect))
(def db (mg/get-db connection database))

(def thumbnail-dir
  (:path (first (mc/find-maps db preferences-collection {:_id "thumbnail-directory"} ))))
(def medium-dir
  (:path (first (mc/find-maps db preferences-collection {:_id "medium-directory"}))))
(def large-dir
  (:path (first (mc/find-maps db preferences-collection {:_id "large-directory"}))))
(def fullsize-dir
  (:path (first (mc/find-maps db preferences-collection {:_id "fullsize-directory"}))))

(def external-viewer
  (:path (first (mc/find-maps db preferences-collection {:_id "external-viewer"}))))

(def default-thumbnail
  (File.
   (:path (first (mc/find-maps db preferences-collection {:_id "thumbnail-default"})))))
(def alternate-thumbnail
  (File.
   (:path (first (mc/find-maps db preferences-collection {:_id "thumbnail-alternate"})))))

(defn thumbnail-file
  "given a string representing an image, returns the File. containing the thumbnail"
  [image-path]
  (File. (str thumbnail-dir "/" image-path)))
(defn medium-file
  "given a string representing an image, returns the File. containing the medium version"
  [image-path]
  (File. (str medium-dir "/" image-path)))
(defn large-file
  "given a string representing an image, returns the File. containing the large version"
  [image-path]
  (File. (str large-dir "/" image-path)))
(defn fullsize-file
  "given a string representing an image, returns the File. containing the fullsize version"
  [image-path]
  (File. (str fullsize-dir "/" image-path)))

(defn get-keyword
  [keyword-name]
  (first (mc/find-maps db keyword-collection {:_id keyword-name})))

(defn sample-thumbnail
  "given a keyword returns the thumbnail image sample File."
  [given-keyword]
  (let [kw (get-keyword given-keyword)]
    (thumbnail-file
     (if (:sample kw)
       (:sample kw)
       (get-best-image database images-collection given-keyword)))))

(defn get-image-list
  [given-keyword]
  (find-images database images-collection "Keywords" given-keyword))

(defn branch?
  [keyword-node]
  (< 0 (count (keyword-node :sub))))

(defn get-children
  [keyword]
  (map get-keyword (keyword :sub)))

(def tree-model
  (simple-tree-model
   branch?
   get-children
   (get-keyword "Root")))

(defn render-file-item
  [renderer {:keys [value]}]
  (config! renderer
          :text (:_id value)))

(defn keypress [e]
  (let [k (.getKeyChar e)]
    (prn k (type k))
    (if (= k \newline)
      (alert "ENTER!")
      (prn "some other key"))))

(defn make-frame []
  (let [image-panel   (label    :id :image
                                :icon alternate-thumbnail
                                :text "errmmmmm, no"
                                :valign :center
                                :halign :center
                                :v-text-position :bottom
                                :h-text-position :center)
        details-panel (scrollable
                       (listbox :id :details
                                :model ["details here"]))
        tree-panel    (scrollable
                       (tree    :id :tree
                                :model tree-model
                                :renderer render-file-item))
        ]
    (frame
     :title "Keyword Explorer"
     :size [1400 :by 800]
     :content
     (border-panel
      :center (left-right-split
               ;; tree-panel
               (top-bottom-split
                image-panel
                details-panel
                :divider-location 1/4)
               tree-panel
               :divider-location 1/5)
      :south  (label :id :status :text "Ready")))))


(defn fill-details
  [details keyword-tree]
  (let [selected-keyword (:_id (last (selection keyword-tree)))
        images           (find-images database images-collection
                                      "Keywords" selected-keyword)
        image-paths      (map image-path images)]
    (config! details :model image-paths)))

(defn fill-image
  [image-pane image-list]
  (let [selected-image (selection image-list)]
    (config! image-pane :icon (thumbnail-file selected-image))))

(defn -main [& args]
  (invoke-later
   (let [f                (make-frame)
         details          (select f [:#details])
         image-pane       (select f [:#image])
         keyword-tree     (select f [:#tree])
         selected-image   (fn [] (let [sel (selection details)]
                                  (if sel
                                    sel
                                    (get-best-image
                                     database
                                     images-collection
                                     (:_id (last (selection keyword-tree)))))))
         quit-handler     (fn [e] (hide! f))
         test-handler     (fn [e] (alert (str (fullsize-file (selected-image)))))
         fullsize-handler (fn [e]
                            (sh external-viewer (str (fullsize-file (selected-image)))))
         large-handler    (fn [e]
                            (sh external-viewer (str (large-file    (selected-image)))))
         medium-handler   (fn [e]
                            (sh external-viewer (str (medium-file   (selected-image)))))
         image-handler    (fn [e] (fill-image image-pane details))]

     (map-key f "T" test-handler)
     (map-key f "Q" quit-handler)
     (map-key f "F" fullsize-handler)
     (map-key f "L" large-handler)
     (map-key f "M" medium-handler)
     ;;(map-key f "O" open-handler)
     (map-key f "shift O" test-handler)
     (listen keyword-tree
             :selection
             (fn [e]
               (if-let [kw (last (selection e))]
                 (let [files (get-children kw)]
                   (config! image-pane
                            :icon (sample-thumbnail (:_id kw))
                            :text (:_id kw))
                   (fill-details details keyword-tree)))))
     (listen details
             :selection
             image-handler)
     (show! f))))
