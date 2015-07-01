(ns slacky.meme
  (:require [clj-http.client :as http]
            [cheshire.core :as json]
            [clj-slack-client
             [rtm-transmit :as tx]]
            [clojure.string :as string]
            [clojure.repl :refer [pst]]))

(def memecaptain-url "http://memecaptain.com/")

(def connection-pool nil
)

;; todo use a connection manager with clj-http or the pool with aleph
  (comment (http/connection-pool {:connection-options {:keep-alive? false}
                                  :connections-per-host 4}))

(defn- poll-for-result [polling-url]
  (println "Polling" polling-url)
  (loop [attempts 0]
    (let [resp (http/get polling-url {:pool connection-pool :follow-redirects false})
          status (:status resp)]

      (cond

        (= 303 status)
        (get-in resp [:headers "location"])

        (< 10 attempts)
        (throw (Exception. (str "Timed out waiting")))

        (= 200 status)
        (do (println "Meme not ready, sleeping")
            (Thread/sleep 2000)
            (recur (inc attempts)))

        :else
        (do (println "Something else happened")
            (throw (ex-info (str "Unexpected response from " polling-url)
                            resp)))))))

(defn- google-image-search [term]
  (println "Googling for images matching" term)
  (let [resp (http/get "http://ajax.googleapis.com/ajax/services/search/images"
                        {:pool connection-pool
                         :headers {:Content-Type "application/json"
                                   :Accept "application/json"}
                         :query-params {:q term
                                        :v 1.0
                                        :rsz 8
                                        :safe "active"}})]

    (when-let [body (and (= 200 (:status resp))
                         (:body resp))]
      (-> body (json/decode keyword) :responseData :results rand-nth :unescapedUrl))))


(defn- create-template [image-url]
  (println "Creating template for image" image-url)
  (let [resp (http/post (str memecaptain-url "/src_images")
                         {:pool connection-pool
                          :headers {:Content-Type "application/json"
                                    :Accept "application/json"}
                          :follow-redirects false
                          :body (json/encode {:url (.trim image-url)})})]

    (println "Template creation responded" resp)

    (if-let [polling-url (and (= 202 (:status resp))
                              (not-empty (get-in resp [:headers "location"])))]

      (do (poll-for-result polling-url)
          (-> resp :body (json/decode keyword) :id))

      (throw (ex-info (str "Unexpected response")
                      resp)))))


(defn- resolve-template-id [term]
  (println "Resolving template for term" term)
  (cond
    (string/blank? term)
    nil

    (re-matches #"^https?://.*$" term)
    (create-template term)

    :else
    (create-template (google-image-search term)))

  ;; allow pre-baked ones?
  )

(defn- create-instance [template-id text-upper text-lower]
  (println "Generating meme based on template" template-id)
  (let [resp (http/post (str memecaptain-url "/gend_images")
                         {:pool connection-pool
                          :headers {:Content-Type "application/json"
                                    :Accept "application/json"}
                          :follow-redirects false
                          :body (json/encode {:src_image_id template-id
                                              :captions_attributes
                                              [{:text text-upper
                                                :top_left_x_pct 0.05
                                                :top_left_y_pct 0
                                                :width_pct 0.9
                                                :height_pct 0.25}
                                               {:text text-lower
                                                :top_left_x_pct 0.05
                                                :top_left_y_pct 0.75
                                                :width_pct 0.9
                                                :height_pct 0.25}]})})]

    (if-let [polling-url (and (= 202 (:status resp))
                              (not-empty (get-in resp [:headers "location"])))]

      (poll-for-result polling-url)

      (throw (ex-info (str "Unexpected response")
                      resp)))))

(defn generate-meme [{:keys [channel text]}]
  (let [[_ template-search text-upper text-lower]
        (map #(.trim %) (re-matches #"\-meme <?([^>]*)>?\s?\|\s?(.*)\s?\|\s?(.*)\s?" text))]

    (when-let [template-id (resolve-template-id template-search)]
      (try
        (let [meme-url (create-instance template-id text-upper text-lower)]
          (tx/say-message channel meme-url))

        (catch Exception e (pst e)))
      )))
