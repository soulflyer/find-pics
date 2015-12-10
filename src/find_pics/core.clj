(ns find-pics.core
  (:use [seesaw core tree])
  (:import [java.io File])
  (:require
   [monger.collection :as mc]
   [monger.core :as mg]
   [monger.operators :refer :all])
  (:gen-class))

(def database "soulflyer")
(def keyword-collection "keywords")
(def connection (mg/connect))
(def db (mg/get-db connection database))

(defn get-keyword
  [keyword-name]
  (first (mc/find-maps db keyword-collection {:_id keyword-name})))

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
  (config! renderer :text (:_id value)))

(defn make-frame []
  (frame
   :title "File Explorer"
   :width 500
   :height 500
   :content

   (left-right-split
    (scrollable (tree    :id :tree :model tree-model :renderer render-file-item))
    (scrollable (listbox :id :list :renderer render-file-item))
    :divider-location 1/3)))

(defn -main [& args]
  (invoke-later
   (let
       [f (make-frame)]
     (listen
      (select f [:#tree]) :selection
      (fn [e]
        (if-let [dir (last (selection e))]
          (let
            [files (get-children dir)]
            (config! (select f [:#list]) :model files)
            ))))
     (-> f
         pack!
         show!))))
