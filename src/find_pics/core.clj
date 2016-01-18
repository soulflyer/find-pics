(ns find-pics.core
  (:import java.io.File)
  (:require [image-lib.core :refer [find-images
                                    image-path
                                    get-best-image]]
            [monger
             [collection :as mc]
             [core :as mg]]
            [seesaw
             [core :refer :all]
             [tree :refer :all]
             [keymap :refer :all]])
;;  (:gen-class)
  )

(def database "soulflyer")
(def keyword-collection "keywords")
(def preferences-collection "preferences")
(def images-collection "images")
(def connection (mg/connect))
(def db (mg/get-db connection database))

(def thumbnail-dir
  (:path (first (mc/find-maps db preferences-collection {:_id "thumbnail-directory"} ))))
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
  (frame
   :title "Keyword Explorer"
   :size [1400 :by 800]
   ;;:listen [:key-typed keypress]
   ;; :width 800
   ;; :height 600
   :content

   (border-panel
    ;; :listen [:key-typed keypress]
    :center (left-right-split
             (scrollable
              (tree    :id :tree
                       :model tree-model
                       :renderer render-file-item))
             (scrollable
              (label :id :image
                     :icon alternate-thumbnail
                     :text "errmmmmm, no"
                     :valign :top
                     :v-text-position :bottom
                     :h-text-position :center))
             :divider-location 7/8)
    :south  (label :id :status :text "Ready"))))

(defn make-frame2 []
  (frame
   :title "Keyword Explorer"
   :size [1400 :by 800]
   :content (scrollable
             ;; (label :text "uh?")
             (tree    :id :tree
                      :model tree-model
                      :renderer render-file-item)
             )))


(defn details-handler
  [e]
  (alert "Pressed ENTER"))

(defn -main [& args]
  (invoke-later
   (let [f (make-frame)
         handler (fn [e] (alert "pressed key"))]
     (map-key f "ENTER" details-handler)
     (listen
      (select f [:#tree])
      :selection
      (fn [e]
        (if-let [kw (last (selection e))]
          (let [files (get-children kw)]
            (config! (select f [:#image])
                     :icon (sample-thumbnail (:_id kw))
                     ;; :text (find-images database images-collection "Keywords" (:_id kw))
                     )))))
     (show! f))))
