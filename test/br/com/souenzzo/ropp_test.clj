(ns br.com.souenzzo.ropp-test
  (:require [br.com.souenzzo.ropp :as ropp]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest]]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [com.wsscode.pathom3.connect.operation :as pco]
            [datascript.core :as ds]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.test :refer [response-for]]
            [midje.sweet :refer [fact =>]]
            [ring.util.mime-type :as mime]))

(pco/defresolver find-pets [env {::ropp/keys [query-params]
                                 ::keys      [petstore-db]}]
  {::pco/output [:petstore.operation/find-pets]}
  (let []
    {:petstore.operation/find-pets {:body   (-> (for [[id name tag] (ds/q '[:find ?id ?name ?tag
                                                                            :in $
                                                                            :where
                                                                            [?e :pet/id ?id]
                                                                            [?e :pet/name ?name]
                                                                            [?e :pet/tag ?tag]]
                                                                      petstore-db)]
                                                  {:id   id
                                                   :name name
                                                   :tag  tag})
                                              (->> (take (:limit query-params 0)))
                                              json/generate-string)
                                    :status 200}}))

(pco/defresolver petstore-db [{::keys [petstore-conn]} _]
  {::petstore-db (ds/db petstore-conn)})

(pco/defmutation add-pet [{::keys [petstore-conn]} {::ropp/keys [body-params]}]
  {::pco/op-name 'petstore.operation/add-pet}
  (let [{:keys [name tag]} body-params]
    (ds/transact! petstore-conn [{:pet/id   1
                                  :pet/name name
                                  :pet/tag  tag}])
    {:status 201}))

(deftest petstore
  (let [open-api (-> "OpenAPI-Specification/examples/v3.0/petstore-expanded.json"
                   io/reader
                   json/parse-stream)
        realworld-routes (ropp/expand-routes
                           (assoc (pci/register [find-pets add-pet petstore-db])
                             ::petstore-conn (ds/create-conn)
                             ::ropp/operation->ident {"findPets" :petstore.operation/find-pets
                                                      "addPet"   'petstore.operation/add-pet}
                             ::ropp/open-api open-api))
        my-routes (route/expand-routes
                    #{["/_healthcheck" :get (fn [_]
                                              {:status 202})
                       :route-name ::healthcheck]})
        routes (concat my-routes realworld-routes)
        service-fn (-> {::http/routes routes}
                     http/default-interceptors
                     http/dev-interceptors
                     http/create-servlet
                     ::http/service-fn)
        url-for (route/url-for-routes routes)]
    (fact
      "_healthcheck"
      (-> (response-for service-fn :get (url-for ::healthcheck))
        :status)
      => 202)
    (fact
      "findPets"
      (-> (response-for service-fn :get (url-for :petstore.operation/find-pets
                                          :params {:limit 3}))
        :body
        (json/parse-string true))
      => [])
    (fact
      "addPet"
      (-> (response-for service-fn :post (url-for (keyword 'petstore.operation/add-pet))
            :headers {"Content-Type" (mime/default-mime-types "json")}
            :body (json/generate-string {:name "Doggo"
                                         :tag  "dog"}))
        :status)
      => 201)
    (fact
      "findPets with a pet"
      (-> (response-for service-fn :get (url-for :petstore.operation/find-pets
                                          :params {:limit 3}))
        :body
        (json/parse-string true))
      => [{:id 1 :name "Doggo" :tag "dog"}])))

(deftest readme-test
  (let [open-api (json/parse-stream (io/reader (io/resource "hello.json")))
        api-routes (ropp/expand-routes
                     (assoc (pci/register (pco/resolver `hello
                                            {::pco/output [::hello]}
                                            (fn [_ _]
                                              {::hello {:status 200
                                                        :body   "World"}})))
                       ::ropp/operation->ident {"Hello" ::hello}
                       ::ropp/open-api open-api))
        my-routes (route/expand-routes
                    #{["/_healthcheck" :get (fn [_]
                                              {:status 202})
                       :route-name ::healthcheck]})
        routes (concat my-routes api-routes)
        service-fn (-> {::http/routes routes}
                     http/default-interceptors
                     http/create-servlet
                     ::http/service-fn)]
    (fact
      (-> (response-for service-fn :get "/hello")
        :body)
      => "World")))
