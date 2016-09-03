(ns find-pics.core
  (:import java.io.File)
  (:require [image-lib.core :refer [db
                                    keyword-collection
                                    image-collection
                                    preference-collection
                                    get-keyword
                                    find-images
                                    find-all-images
                                    image-path
                                    best-image
                                    best-sub-image
                                    preference
                                    preference!
                                    add-keyword
                                    move-keyword
                                    delete-keyword
                                    disconnect-keyword
                                    merge-keyword
                                    safe-delete-keyword
                                    rename-keyword
                                    find-parents
                                    first-parent
                                    all-keywords
                                    used-keywords
                                    add-missing-keywords]]
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

(def thumbnail-dir   (preference  "thumbnail-directory"))
(def medium-dir      (preference     "medium-directory"))
(def large-dir       (preference      "large-directory"))
(def fullsize-dir    (preference   "fullsize-directory"))
(def external-viewer (preference      "external-viewer"))
(def window-title    (preference         "window-title"))

(def default-thumbnail   (File. (preference "thumbnail-default")))
(def alternate-thumbnail (File. (preference "thumbnail-default")))

(def help-text ["h   help screen"
                "o   open medium size pic in external viewer"
                "O   open all medium size pics"
                "l   open large"
                "L   open all Large"
                "f   open fullsize"
                "F   open all fullsize"
                "a   display all pic paths including sub keywords"
                "b   display the 'best' pic for this keyword"
                "s   save the selected pic as the sample for this keyword"
                "n   add new keyword"
                "d   delete keyword (must have no sub keywords)"
                "m   move keyword"
                "r   rename keyword"
                "R   refresh the keyword tree"
                "p   list parents of selected keyword"
                "x   disconnect keyword"
                "c   combine selected keyword with another"])

(defn thumbnail-file [image-path] (File. (str thumbnail-dir "/" image-path)))
(defn medium-file    [image-path] (File. (str medium-dir "/" image-path)))
(defn large-file     [image-path] (File. (str large-dir "/" image-path)))
(defn fullsize-file  [image-path] (File. (str fullsize-dir "/" image-path)))

(defn sample-thumbnail
  "given a keyword returns the thumbnail image sample File."
  [given-keyword]
  (let [kw (get-keyword given-keyword)]
    (thumbnail-file
     (or
       (:sample kw)
       (image-path (best-image given-keyword))))))

(defn get-image-list
  [given-keyword]
  (find-images "Keywords" given-keyword))

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
                                   (image-path (best-image (selected-keyword))))))
         fill-all-details (fn [keyword]
                            (let [images (find-all-images keyword-collection keyword)
                                  image-paths (map image-path images)]
                              (config! details :model image-paths)))
         fill-details     (fn [keyword]
                            (let [images (find-images "Keywords" keyword)
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
         save-handler     (fn [e] (mc/update-by-id
                                  db keyword-collection
                                  (selected-keyword)
                                  (conj {:sample (selected-image)}
                                        {:sub (:sub (mc/find-map-by-id
                                                     db
                                                     keyword-collection
                                                     (selected-keyword)))})))

         all-handler      (fn [e] (fill-all-details (selected-keyword)))
         refresh-handler  (fn [e] (doall
                                  (add-missing-keywords)
                                  (config! keyword-tree :model (load-model))))
         best-handler     (fn [e]
                            (config! image-pane
                                     :icon
                                     (thumbnail-file
                                      (image-path (best-sub-image (selected-keyword))))))
         help-handler     (fn [e] (config! details :model help-text))
         fullsize-handler (fn [e] (sh external-viewer (str (fullsize-file (selected-image)))))
         large-handler    (fn [e] (sh external-viewer (str (large-file    (selected-image)))))
         medium-handler   (fn [e] (sh external-viewer (str (medium-file   (selected-image)))))
         all-large-handler    (fn [e] (open-all large-dir))
         all-medium-handler   (fn [e] (open-all medium-dir))
         all-fullsize-handler (fn [e] (open-all fullsize-dir))
         image-handler    (fn [e] (config! image-pane
                                          :icon (thumbnail-file (selection details)) ))
         add-keyword-handler    (fn [e]
                                  (let [selected (selected-keyword)
                                        new (input e (str "Add new keyword under " selected))]
                                    (if new
                                      (doall
                                       (add-keyword new selected)
                                       (config! keyword-tree :model (load-model))))))
         delete-keyword-handler (fn [e]
                                  (safe-delete-keyword (selected-keyword))
                                  (config! keyword-tree :model (load-model)))
         rename-keyword-handler (fn [e]
                                  (let [selected (selected-keyword)
                                        new (input e (str "Rename " selected " to:")
                                                   :value selected)]
                                    (if new
                                      (doall
                                       (rename-keyword selected new)
                                       (config! keyword-tree :model (load-model))))))
         disconnect-handler     (fn [e]
                                  (let [selected (selected-keyword)
                                        new (input e (str "Disconnect " selected " from:")
                                            :value (:_id (last (find-parents selected))))]
                                    (if new
                                      (doall
                                       (disconnect-keyword selected new)
                                       (config! keyword-tree :model (load-model))))))
         move-keyword-handler   (fn [e]
                                  (let [selected (selected-keyword)
                                        new (input e (str "Move " selected " to:"))
                                        parent-id (:_id (first-parent selected))]
                                    (if new
                                      (doall
                                       (move-keyword selected parent-id new)
                                       (config! keyword-tree :model (load-model))))))
         merge-keyword-handler  (fn [e]
                                  (let [selected (selected-keyword)
                                        existing (input e (str "Combine " selected " with:")
                                                        :value selected)]
                                    (if existing
                                      (doall
                                       (merge-keyword selected existing)
                                       (config! keyword-tree :model (load-model))))))
         parents-handler        (fn [e]
                                  (let [parents (map :_id (find-parents (selected-keyword)))
                                        parent-list (reduce
                                                     #(str %1 " -- " %2) parents)]
                                    (alert parent-list)))]

     (native!)
     (map-key f "Q" quit-handler)
     (map-key f "F" fullsize-handler)
     (map-key f "L" large-handler)
     (map-key f "O" medium-handler)
     (map-key f "shift O" all-medium-handler)
     (map-key f "shift L" all-large-handler)
     (map-key f "shift F" all-fullsize-handler)
     (map-key f "A" all-handler)
     (map-key f "B" best-handler)
     (map-key f "S" save-handler)
     (map-key f "H" help-handler)
     (map-key f "shift R" refresh-handler)
     (map-key f "R" rename-keyword-handler)
     (map-key f "N" add-keyword-handler)
     (map-key f "D" delete-keyword-handler)
     (map-key f "M" move-keyword-handler)
     (map-key f "X" disconnect-handler)
     (map-key f "P" parents-handler)
     (map-key f "C" merge-keyword-handler)

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
