(ns petstore.main
  (:require [io.pedestal.http :as http]
            [datascript.core :as ds]
            [br.com.souenzzo.ropp :as ropp]
            [io.pedestal.http.route :as route]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [hiccup2.core :as h]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [com.wsscode.pathom3.connect.operation :as pco]
            [datascript.core :as d]
            [ring.util.mime-type :as mime])
  (:import (java.nio.charset StandardCharsets)))

(pco/defresolver find-pets [env {::ropp/keys [query-params]
                                 ::keys      [pets-db]}]
  {::pco/output [::find-pets]}
  (let [{::ropp/keys [query-params]} query-params]
    {::find-pets {:body   (-> (for [[id name tag] (ds/q '[:find ?id ?name ?tag
                                                          :in $
                                                          :where
                                                          [?e :petstore.pet/id ?id]
                                                          [?e :petstore.pet/name ?name]
                                                          [?e :petstore.pet/tag ?tag]]
                                                    pets-db)]
                                {:id   id
                                 :name name
                                 :tag  tag})
                            (->> (take (:limit query-params 10)))
                            json/generate-string)
                  :status 200}}))

(pco/defresolver find-pet-by-id [env {::ropp/keys [path-params]
                                      ::keys      [pets-db]}]
  {::pco/output [::find-pet-by-id]}
  (let []
    {::find-pet-by-id {:body   (-> (for [[id name tag] (ds/q '[:find ?id ?name ?tag
                                                               :in $
                                                               :where
                                                               [?e :petstore.pet/id ?id]
                                                               [?e :petstore.pet/name ?name]
                                                               [?e :petstore.pet/tag ?tag]]
                                                         pets-db)]
                                     {:id   id
                                      :name name
                                      :tag  tag})
                                 first
                                 json/generate-string)
                       :status 200}}))


(pco/defresolver pets-db [{::keys [pets-conn]} _]
  {::pets-db (ds/db pets-conn)})

(pco/defmutation add-pet [{::keys [pets-conn]} {::ropp/keys [body-params]}]
  {}
  (let [{:keys [name tag]} body-params]
    (ds/transact! pets-conn [{:petstore.pet/id   1
                              :petstore.pet/name name
                              :petstore.pet/tag  tag}])
    {:status 201}))


(pco/defmutation delete-pet [{::keys [pets-conn]} {::ropp/keys [path-params]}]
  {}
  (let []
    (d/transact! pets-conn [[:db/retractEntity 1]])
    {:status 200}))


(defn app
  [service-map]
  (assoc service-map
    ::pets-conn (ds/create-conn)))

(defn routes
  [service-map]
  (let [open-api (-> "../../OpenAPI-Specification/examples/v3.0/petstore-expanded.json"
                   io/reader
                   json/parse-stream)
        operation->ident {"findPets"       ::find-pets
                          "find pet by id" ::find-pet-by-id
                          "deletePet"      `delete-pet
                          "addPet"         `add-pet}
        env (merge service-map
              (pci/register [pets-db add-pet find-pets find-pet-by-id delete-pet])
              {::ropp/operation->ident operation->ident
               ::ropp/open-api         open-api})
        routes (concat
                 (ropp/expand-routes env)
                 (route/expand-routes `#{["/spec" :get ~(fn [_]
                                                          {:body    (io/input-stream "../../OpenAPI-Specification/examples/v3.0/petstore-expanded.json")
                                                           :headers {"Content-Type" (mime/default-mime-types "json")}
                                                           :status  200})
                                          :route-name ::spec]
                                         ["/swagger-ui-dist/*path" :get ~(fn [{:keys [path-params]}]
                                                                           (let [{:keys [path]} path-params
                                                                                 body (some-> "META-INF/resources/webjars/swagger-ui-dist/3.37.2/"
                                                                                        (str path)
                                                                                        io/resource
                                                                                        io/input-stream)]
                                                                             (when body
                                                                               {:body    body
                                                                                :headers {"Content-Type" (mime/ext-mime-type path)}
                                                                                :status  200})))
                                          :route-name ::swagger-ui-dist]
                                         ["/" :get ~(fn [_]
                                                      {:body    (->> [:html
                                                                      [:head
                                                                       [:link {:rel  "icon"
                                                                               :href "data:"}]
                                                                       [:meta {:charset (str StandardCharsets/UTF_8)}]
                                                                       [:link {:rel  "stylesheet"
                                                                               :type "text/css"
                                                                               :href "/swagger-ui-dist/swagger-ui.css"}]
                                                                       [:script
                                                                        {:src "/swagger-ui-dist/swagger-ui-bundle.js"}]
                                                                       [:script
                                                                        {:src "/swagger-ui-dist/swagger-ui-standalone-preset.js"}]
                                                                       [:script
                                                                        {:src "/swagger-ui-dist/swagger-ui-standalone-preset.js"}]
                                                                       [:title "Hello"]]
                                                                      [:body
                                                                       [:div {:id "app"}]
                                                                       [:script (h/raw "
    let ui = SwaggerUIBundle({
      url: '/spec',
      dom_id: '#app',
      deepLinking: true,
      presets: [
        SwaggerUIBundle.presets.apis,
        SwaggerUIStandalonePreset
      ],
      layout: 'StandaloneLayout'
    })
")]]]
                                                                  (h/html {:mode :html})
                                                                  (str "<!DOCTYPE html>\n"))
                                                       :headers {"Content-Security-Policy" ""
                                                                 "Content-Type"            (mime/default-mime-types "html")}
                                                       :status  200})
                                          :route-name ::index]}))]
    routes))

(defn service
  [service-map]
  (-> service-map
    (assoc ::http/routes (fn []
                           (routes service-map)))
    http/default-interceptors))

(defn -main
  [& _]
  (prn ::TODO))
