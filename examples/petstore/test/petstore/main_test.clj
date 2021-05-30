(ns petstore.main-test
  (:require [petstore.main :as petstore]
            [midje.sweet :refer [fact =>]]
            [io.pedestal.http :as http]
            [br.com.souenzzo.ropp :as ropp]
            [io.pedestal.test :refer [response-for]]
            [clojure.test :refer [deftest]]
            [ring.util.mime-type :as mime]
            [cheshire.core :as json]))


(deftest hello
  (let [app (petstore/app {})
        service-fn (-> (petstore/service app)
                     http/create-servlet
                     ::http/service-fn)]
    (fact
      "findPets"
      (-> (response-for service-fn :get "/pets")
        :body
        (json/parse-string true))
      => [])
    (fact
      "addPet"
      (-> (response-for service-fn :post "/pets"
            :headers {"Content-Type" (mime/default-mime-types "json")}
            :body (json/generate-string {:name "Doggo"
                                         :tag  "dog"}))
        :status)
      => 201)
    (fact
      "pet by id"
      (-> (response-for service-fn :get "/pets/1")
        :body
        (json/parse-string true))
      => {:id 1 :name "Doggo" :tag "dog"})
    (fact
      "findPets with a pet"
      (-> (response-for service-fn :get "/pets")
        :body
        (json/parse-string true))
      => [{:id 1 :name "Doggo" :tag "dog"}])
    (fact
      "delete pet"
      (-> (response-for service-fn :delete "/pets/1")
        :status)
      => 200)
    (fact
      "findPets with a pet"
      (-> (response-for service-fn :get "/pets")
        :body
        (json/parse-string true))
      => [])))


(deftest linter
  (let [app (petstore/app {})
        service-map (petstore/service app)]
    (fact
      (ropp/explain-data service-map)
      => empty?)))
