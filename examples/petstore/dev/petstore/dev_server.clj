(ns petstore.dev-server
  (:require [petstore.main :as petstore]
            [io.pedestal.http :as http]))


(defonce state (atom nil))

(defn -main
  [& _]
  (swap! state (fn [st]
                 (some-> st http/stop)
                 (-> st
                   (or (petstore/app {::http/type  :jetty
                                      ::http/port  8080
                                      ::http/join? false}))
                   petstore/service
                   http/create-server
                   http/start))))
