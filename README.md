# ropp

> A simple way to write HTTP API's following OpenAPI spec with pathom.

This library bind together these technologies:

- [pathom3](https://pathom3.wsscode.com/)
- [pedestal](https://pedestal.io/)
- [OpenAPI V3](https://openapis.org/)

You should be comfortable with these.

This library aims to be **small** and **easy**.

I intend to develop an **elegant**, **simple** and **easy** with the experience and feedback from this one in the
future.

## Usage

I don't like to provide templates for a simple reason: they do not compose. If you wanna play with `pedestal`, `jdbc`
and `pathom` and have these 3 templates, you can't simply compose them

`create-template a + b + c`

The way to solve that it explain to the user how to compose the library with the existing project.

### Project setup

Add to your `deps.edn`

```clojure 
br.com.souenzzo/ropp {:git/url "https://github.com/souenzzo/hopp"
                      :sha     "<...>"}
```

Make sure that you have an `OpenAPI` spec.

It can be both `JSON` or `YAML`. After you parse it, you should have a clojure data-structure like this:

```clojure
;; Every key should be a string
{"openapi" "3.0.0",
 "info"    {"title" "hello api", "version" "v1"},
 "paths"   {"/hello" {"post" {"operationId" "Hello",
                              "responses"   {"200" {"description" "Return a basic response body in JSON",
                                                    "content"     {"application/json" {"schema" {"type"       "object",
                                                                                                 "properties" {"hello" {"type" "string"}}}}}}}}}}}
```

### Add to existing pedestal service

Let's start with a simple pedestal example

```clojure
#_(require '[io.pedestal.http :as http])

(defn index
  [_]
  {:status 200
   :body   "Index page"})

(def routes
  `#{["/index" :get index]})

(def service
  {:env                 :prod
   ::http/routes        routes
   ::http/resource-path "/public"
   ::http/type          :jetty
   ::http/port          8080})
```

Now let's grow it and add `ropp` library

```clojure
#_(require '[io.pedestal.http :as http]
    ;; new requires:
    '[io.pedestal.http.route :as route]
    '[cheshire.core :as json]
    '[com.wsscode.pathom3.connect.indexes :as pci]
    '[com.wsscode.pathom3.connect.operation :as pco]
    '[br.com.souenzzo.ropp :as ropp])

(defn index
  [_]
  {:status 200
   :body   "Index page"})

(def routes
  `#{["/index" :get index]})


;; New: we should have at least one resolver for each OpenAPI operation
(pco/defresolver hello [env input]
  {::pco/output [::hello]}
  (let []
    {::hello {:body   (json/generate-string {:hello "World"})
              :status 200}}))


;; New: We will generate a new route-list from the spec + pathom metadata
(def api-routes
  (ropp/expand-routes
    (assoc (pci/register [hello])
      ;; Here you "bind" the `"Hello"` operation, defined by `operationId` in OpenAPI spec
      ;; To the `::hello` attribute, defined in some resolver.
      ::ropp/operation->ident {"Hello" ::hello}
      ;; Your API spec should be in `resources/hello.json`
      ::ropp/open-api (json/parse-stream (io/reader "resources/hello.json")))))

(def service
  {:env                 :prod
   ::http/routes        (concat
                          ;; New: You can compose your old routes with the new ones by simply
                          ;; concat them
                          (route/expand-routes routes)
                          api-routes)
   ::http/resource-path "/public"
   ::http/type          :jetty
   ::http/port          8080})
```

Now you can continue to develop your pedestal app.

## Testing and guarantees

By default, `ropp` will not fail for missing operations or extra-defined operations.

To ensure that your implementation fit all OpenAPI requirements, we will provide another API, that will scan your pathom
index with ropp parameters and make sure that they match.

## Procedure examples

### Add a new route

1. Update the OpenAPI spec: `"operationId": "newEndpoint"`
1. Add a new element in `operation->ident` binding `"newEndpoint" :app/new-endpoint`
1. Create a new `pco/defresolver` that output's `:app/new-endpoint`
1. Add the new resolver into your register

