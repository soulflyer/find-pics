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

(defn get-keyword
  [keyword-name]
  (let [connection (mg/connect)
        db (mg/get-db connection database)]
    (first (mc/find-maps db keyword-collection {:_id keyword-name}))))



(defn branch?
  [keyword-node]
  ;;(nil? (keyword-node :sub))
  (= 0 (count (keyword-node :sub))))

(defn get-subdirs
  [d]
  (filter #(.isDirectory %) (.listFiles d))
  )

;; (defn get-children
;;   [keyword-name]
;;   (let [kw (get-keyword keyword-name)]
;;     (map get-keyword (kw :sub))))
(defn get-children
  [keyword]
  (map get-keyword (keyword :sub)))

;; ;;File explorer stuff ******************************

;; (def tree-model
;;   (simple-tree-model
;;    #(.isDirectory %)
;;    ;;   (fn [f] (filter #(.isDirectory %) (.listFiles f)))
;;    get-subdirs
;;    (File. ".")))

;; (def chooser (javax.swing.JFileChooser.))

;; (defn render-file-item
;;   [renderer {:keys [value]}]
;;   (config! renderer :text (.getName value)
;;            ;;:icon (.getIcon chooser value)
;;            ))

;; Keyword listing stuff **************************

(def tree-model
  (simple-tree-model
   branch?
   get-children
   (get-keyword "Root")))

(defn render-file-item
  [renderer {:keys [value]}]
  (config! renderer :text (:_id value)))

;;**************************************************

(defn make-frame []
  (frame
   :title "File Explorer"
   :width 500
   :height 500
   :content
   (border-panel
    ;;:north  (label :id :current-dir :text "Location")
    :center (left-right-split
             (scrollable (tree    :id :tree :model tree-model :renderer render-file-item))
             (scrollable (listbox :id :list :renderer render-file-item))
             :divider-location 1/3)
    ;;:south  (label :id :status :text "Ready")
    )

   ;;on-close :exit
   ))

(defn -main [& args]
  (invoke-later
   (let
       [f (make-frame)]
     (listen
      (select f [:#tree]) :selection
      (fn [e]
        (if-let [dir (last (selection e))]
          (let
            ;;  [files (.listFiles dir)]
            [files (get-children dir)]
      ;;      (config! (select f [:#status]) :text (format "Ready (%d items)" (count files)))
            (config! (select f [:#list]) :model files)
            ))))
     (-> f
         pack!
         show!))))
