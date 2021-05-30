(ns petstore.main
  (:require [io.pedestal.http :as http]
            [datascript.core :as ds]
            [br.com.souenzzo.ropp :as ropp]
            [io.pedestal.http.route :as route]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [com.wsscode.pathom3.connect.operation :as pco]
            [datascript.core :as d]))

(pco/defresolver find-pets [env {::keys [pets-db]}]
  {::pco/params [::ropp/query-params]
   ::pco/output [::find-pets]}
  (let [{::ropp/keys [query-params]} (pco/params env)]
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

(pco/defresolver find-pet-by-id [env {::keys [pets-db]}]
  {::pco/params [::ropp/query-params]
   ::pco/output [::find-pet-by-id]}
  (let [{::ropp/keys [path-params]} (pco/params env)]
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
  [_]
  {::pets-conn (ds/create-conn)})

(defn service
  [service-map]
  (let [open-api (-> "OpenAPI-Specification/examples/v3.0/petstore-expanded.json"
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
                 (route/expand-routes `#{["/" :get ~(fn [_]
                                                      {:status 202})
                                          :route-name ::index]}))]
    (-> service-map
      (merge env {::http/routes routes})
      http/default-interceptors)))

(defn -main
  [& _]
  (prn ::TODO))
