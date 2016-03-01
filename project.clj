(defproject find-pics "0.1.0-SNAPSHOT"
  :description "Search for images containing specified keywords"
  :url "http://githube.com/soulflyer/find-pics"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [seesaw "1.4.5"]
                 [image-lib "0.1.1-SNAPSHOT"]
                 [com.novemberain/monger "3.0.1"]
                 [org.clojure/tools.cli "0.3.3"]]
  :main find-pics.core
  :bin {:name "keyword-search"
        :bin-path "~/bin"}
  :jvm-opts ["-Xdock:name=Keyword-Search"])
