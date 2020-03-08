(defproject json-utils "0.1.2"
  :description "A sets of utils for working with Json in Clojure/script."

  :url "https://clojars.org/json-utils"

  :license {:name "MIT"
            :url "https://opensource.org/licenses/MIT"}

  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/data.json "0.2.7"]
                 [org.clojure/core.cache "0.8.2"]
                 [org.clojure/clojurescript "1.10.597"]
	         [org.clojars.mmb90/cljs-cache "0.1.4"]
                 [funcool/httpurr "2.0.0"]
                 [funcool/promesa "5.1.0"]]

  :deploy-repositories {"clojars"  {:sign-releases false :url "https://clojars.org/repo"}
                        "snapshots" {:url "https://clojars.org/repo"}})
