(ns find-pics.core
  (:import java.io.File)
  (:require [image-lib.core :refer [find-images
                                    find-all-images
                                    image-path
                                    best-image
                                    preference]]
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

(def thumbnail-dir   (preference db "preferences" "thumbnail-directory"))
(def medium-dir      (preference db "preferences"    "medium-directory"))
(def large-dir       (preference db "preferences"     "large-directory"))
(def fullsize-dir    (preference db "preferences"  "fullsize-directory"))
(def external-viewer (preference db "preferences" "external-viewer"))
(def window-title    (preference db "preferences" "window-title"))

(def default-thumbnail   (File. (preference db "preferences" "thumbnail-default")))
(def alternate-thumbnail (File. (preference db "preferences" "thumbnail-default")))

(def help-text ["H   help screen"
                "M   open medium size pic in external viewer"
                "L   open large"
                "F   open fullsize"
                "A   display all pic paths including sub keywords"
                "B   display the 'best' pic for this keyword"
                "S   save the selected pic as the sample for this keyword"])

(defn thumbnail-file [image-path] (File. (str thumbnail-dir "/" image-path)))
(defn medium-file    [image-path] (File. (str medium-dir "/" image-path)))
(defn large-file     [image-path] (File. (str large-dir "/" image-path)))
(defn fullsize-file  [image-path] (File. (str fullsize-dir "/" image-path)))

(defn get-keyword
  [keyword-name]
  (first (mc/find-maps db keyword-collection {:_id keyword-name})))

(defn sample-thumbnail
  "given a keyword returns the thumbnail image sample File."
  [given-keyword]
  (let [kw (get-keyword given-keyword)]
    (thumbnail-file
     (or
       (:sample kw)
       ;; (image-path (best-image db images-collection keyword-collection given-keyword))
       (image-path (best-image db images-collection given-keyword))
       ))))

(defn get-image-list
  [given-keyword]
  (find-images db images-collection "Keywords" given-keyword))

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
                                :model help-text))
        tree-panel    (scrollable
                       (tree    :id :tree
                                :model tree-model
                                :renderer render-file-item))]
    (frame
     :title window-title
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
               :divider-location 1/4)
      :south  (label :id :status :text "Ready")))))

(defn fill-image
  [image-pane image-list]
  (let [selected-image (selection image-list)]
    (config! image-pane :icon (thumbnail-file selected-image))))

(defn -main [& args]
  (invoke-later
   (let [
         f                (make-frame)
         details          (select f [:#details])
         image-pane       (select f [:#image])
         keyword-tree     (select f [:#tree])
         selected-keyword (fn [] (:_id (last (selection keyword-tree))))
         selected-image   (fn [] (let [sel (selection details)]
                                  (or
                                   sel
                                   (image-path (best-image db images-collection
                                                           (selected-keyword))))))

         fill-all-details (fn [keyword]
                            (let [images (find-all-images db images-collection
                                                          keyword-collection keyword)
                                  image-paths (map image-path images)]
                              (config! details :model image-paths)))
         fill-details     (fn [keyword]
                            (let [images (find-images db images-collection
                                                      "Keywords" keyword)
                                  image-paths (map image-path images)]
                              (config! details :model image-paths)))

         quit-handler     (fn [e] (hide! f))
         test-handler     (fn [e] (alert (fullsize-file (selected-image))))
         save-handler     (fn [e] (mc/update-by-id
                                  db keyword-collection
                                  (selected-keyword)
                                  (conj {:sample (selected-image)}
                                        {:sub (:sub (mc/find-map-by-id
                                                     db
                                                     keyword-collection
                                                     (selected-keyword)))})))

         all-handler      (fn [e] (fill-all-details (selected-keyword)))
         best-handler     (fn [e]
                            (config! image-pane
                                     :icon
                                     (thumbnail-file
                                      (image-path
                                       (best-image db images-collection
                                                   keyword-collection (selected-keyword))))))
         help-handler     (fn [e] (config! details :model help-text))
         fullsize-handler (fn [e] (sh external-viewer (str (fullsize-file (selected-image)))))
         large-handler    (fn [e] (sh external-viewer (str (large-file    (selected-image)))))
         medium-handler   (fn [e] (sh external-viewer (str (medium-file   (selected-image)))))
         ;; medium-all-handler (fn [e] (sh external-viewer (str (map medium-file   ))))
         image-handler    (fn [e] (fill-image image-pane details))]

     (native!)
     (map-key f "T" test-handler)
     (map-key f "Q" quit-handler)
     (map-key f "F" fullsize-handler)
     (map-key f "L" large-handler)
     (map-key f "M" medium-handler)
     ;;(map-key f "O" open-handler)
     (map-key f "A" all-handler)
     (map-key f "B" best-handler)
     (map-key f "shift O" test-handler)
     (map-key f "S" save-handler)
     (map-key f "H" help-handler)
     (listen keyword-tree
             :selection
             (fn [e]
               (if-let [kw (last (selection e))]
                 (let [files (get-children kw)]
                   (config! image-pane
                            :icon (sample-thumbnail (:_id kw))
                            :text (:_id kw))
                   (fill-details (selected-keyword))))))
     (listen details
             :selection
             image-handler)
     (show! f))))
