(ns find-pics.core
  (:use [seesaw core tree]
        [find-images.core :exclude (-main)])
  (:import [java.io File])
  (:require
   [monger.collection :as mc]
   [monger.core :as mg]
   [monger.operators :refer :all]
   )
  (:gen-class))

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

(defn get-best-image
  [given-keyword]
  (thumbnail-file
   (image-path
    (last
     (sort-by :Rating (find-images database images-collection :Keywords given-keyword))))))

(defn sample-thumbnail
  "given a keyword returns the thumbnail image sample File."
  [given-keyword]
  (let [kw (get-keyword given-keyword)]
    (if (:sample kw)
      (thumbnail-file (:sample kw))
      (get-best-image given-keyword))))

(defn branch?
  [keyword-node]
  ;;(complement (nil? (keyword-node :sub)))
  (< 0 (count (keyword-node :sub)))
  ;;true
  )

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
          :text (:_id value)
          ;;:icon default-thumbnail
          ))

(defn make-frame []
  (frame
   :title "Keyword Explorer"
   :width 800
   :height 600
   :content

   (border-panel
    :center (left-right-split
             (scrollable (tree    :id :tree :model tree-model :renderer render-file-item))
             (scrollable
              (label :id :image :icon  default-thumbnail))
             :divider-location 1/3)
    :south  (label :id :status :text "Ready"))))

(defn -main [& args]
  (invoke-later
   (let
       [f (make-frame)]
     (listen
      (select f [:#tree]) :selection
      (fn [e]
        (if-let [kw (last (selection e))]
          (let
            [files (get-children kw)]
            (config! (select f [:#image]) :icon (sample-thumbnail (:_id kw)))
            ))))
     (-> f
         pack!
         show!))))
