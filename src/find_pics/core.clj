(ns find-pics.core
  (:import java.io.File)
  (:require [image-lib.core :refer [find-images
                                    find-all-images
                                    image-path
                                    best-image
                                    preference
                                    preference!
                                    add-keyword
                                    move-keyword
                                    delete-keyword
                                    find-parents
                                    all-ids
                                    used-keywords]]
            [clojure.string :refer [split
                                    join
                                    replace]]
            [clojure.set    :refer [difference]]
            [clojure.java.shell :refer [sh]]
            [monger
             [collection :as mc]
             [core :as mg]
             [operators :refer :all]]
            [seesaw
             [core :refer :all]
             [tree :refer :all]
             [keymap :refer :all]])
  (:gen-class))

(def database                    "photos")
(def keyword-collection        "keywords")
(def preferences-collection "preferences")
(def images-collection           "images")
(def connection (mg/connect))
(def db (mg/get-db connection database))

(def thumbnail-dir   (preference db "preferences" "thumbnail-directory"))
(def medium-dir      (preference db "preferences"    "medium-directory"))
(def large-dir       (preference db "preferences"     "large-directory"))
(def fullsize-dir    (preference db "preferences"  "fullsize-directory"))
(def external-viewer (preference db "preferences"     "external-viewer"))
(def window-title    (preference db "preferences"        "window-title"))

(def default-thumbnail   (File. (preference db "preferences" "thumbnail-default")))
(def alternate-thumbnail (File. (preference db "preferences" "thumbnail-default")))

(def help-text ["h   help screen"
                "m   open medium size pic in external viewer"
                "M   open all medium size pics"
                "l   open large"
                "L   open all Large"
                "f   open fullsize"
                "F   open all fullsize"
                "a   display all pic paths including sub keywords"
                "b   display the 'best' pic for this keyword"
                "s   save the selected pic as the sample for this keyword"
                "d   delete keyword (must have no sub keywords)"
                "D   move keyword"
                "r   refresh the keyword tree"])

(defn thumbnail-file [image-path] (File. (str thumbnail-dir "/" image-path)))
(defn medium-file    [image-path] (File. (str medium-dir "/" image-path)))
(defn large-file     [image-path] (File. (str large-dir "/" image-path)))
(defn fullsize-file  [image-path] (File. (str fullsize-dir "/" image-path)))

(defn get-keyword
  [keyword-name]
  (first (mc/find-maps db keyword-collection {:_id keyword-name})))

(defn all-keywords
  []
  (all-ids db keyword-collection))

(defn rename-keyword
  "Changes the keyword including any references in parents. Doesn't change the original images"
  [db keyword-collection old-keyword new-keyword]
  (let [parents (find-parents db keyword-collection old-keyword)
        parent  (:_id (first parents))
        children (:sub (get-keyword old-keyword))]
    (add-keyword db keyword-collection new-keyword parent)
    (doall (map #(move-keyword db keyword-collection % old-keyword new-keyword) children))
    (delete-keyword db keyword-collection old-keyword)))

(defn safe-delete-keyword
  "Delete a keyword, but only if it has no sub keywords"
  [db keyword-collection kw parent]
  (let [keyword (get-keyword kw)]
    (if (= 0 (count (:sub keyword)))
      (delete-keyword db keyword-collection kw parent))))

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

(defn load-model []
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
                                :text "keyword browser"
                                :valign :center
                                :halign :center
                                :v-text-position :bottom
                                :h-text-position :center)
        details-panel (scrollable
                       (listbox :id :details
                                :model help-text))
        tree-panel    (scrollable
                       (tree    :id :tree
                                :model (load-model)
                                :renderer render-file-item))]
    (frame
     :title window-title
     :size [1400 :by 800]
     :content
     (border-panel
      :center (left-right-split
               (top-bottom-split
                image-panel
                details-panel
                :divider-location 1/4)
               tree-panel
               :divider-location 1/4)
      :south  (label :id :status :text "Ready")))))

(defn -main [& args]
  (invoke-later
   (let [f                (make-frame)
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

         open-all         (fn [size-dir]
                            (sh "xargs" external-viewer
                                :in (join " "
                                          (map #(str size-dir "/" %)
                                               (split
                                                (replace
                                                 (str (config details :model))
                                                 #"[\[\]]" "") #", ")))))

         quit-handler     (fn [e] (hide! f))
         test-handler     (fn [e] (alert
                                  (str
                                   (split
                                    (replace
                                     (str
                                      (config details :model)) #"[\[\]]" "") #", "))))
         save-handler     (fn [e] (mc/update-by-id
                                  db keyword-collection
                                  (selected-keyword)
                                  (conj {:sample (selected-image)}
                                        {:sub (:sub (mc/find-map-by-id
                                                     db
                                                     keyword-collection
                                                     (selected-keyword)))})))

         all-handler      (fn [e] (fill-all-details (selected-keyword)))
         refresh-handler  (fn [e]
                            (config! keyword-tree
                                     :model (load-model)))
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
         all-large-handler    (fn [e] (open-all large-dir))
         all-medium-handler   (fn [e] (open-all medium-dir))
         all-fullsize-handler (fn [e] (open-all fullsize-dir))
         image-handler    (fn [e] (config! image-pane
                                          :icon (thumbnail-file (selection details)) ))
         add-keyword-handler  (fn [e] (let [ new-keyword "New Keyword"]
                                       (add-keyword
                                        db keyword-collection
                                        (input e (str "Enter new keyword under "
                                                      (selected-keyword)))
                                        (selected-keyword))))
         delete-keyword-handler (fn [e]
                                  (safe-delete-keyword
                                   db keyword-collection
                                   (selected-keyword)
                                   (:_id (first
                                          (find-parents db keyword-collection
                                                        (selected-keyword))))))
         rename-keyword-handler (fn [e]
                                  (rename-keyword
                                   db keyword-collection
                                   (selected-keyword)
                                   (input e (str "Rename "
                                                 (selected-keyword)
                                                 " to:"))))
         move-keyword-handler (fn [e]
                                (move-keyword
                                 db keyword-collection
                                 (selected-keyword)
                                 (:_id (first
                                        (find-parents db keyword-collection
                                                      (selected-keyword))))
                                 (input e (str "Move "
                                               (selected-keyword)
                                               " to:"))))]

     (native!)
     (map-key f "T" test-handler)
     (map-key f "Q" quit-handler)
     (map-key f "F" fullsize-handler)
     (map-key f "L" large-handler)
     (map-key f "M" medium-handler)
     (map-key f "shift M" all-medium-handler)
     (map-key f "shift L" all-large-handler)
     (map-key f "shift F" all-fullsize-handler)
     (map-key f "A" all-handler)
     (map-key f "B" best-handler)
     (map-key f "S" save-handler)
     (map-key f "H" help-handler)
     (map-key f "R" refresh-handler)
     (map-key f "shift R" rename-keyword-handler)
     (map-key f "N" add-keyword-handler)
     (map-key f "D" delete-keyword-handler)
     (map-key f "shift D" move-keyword-handler)

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
