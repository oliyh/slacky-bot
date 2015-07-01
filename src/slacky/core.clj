(ns slacky.core
  (:gen-class)
  (:require
   [clj-slack-client
    [core :as slack]
    [team-state :as state]
    [web :as web]
    [rtm-transmit :as tx]]))

(def ^:dynamic *api-token* nil)

(defn- find-command [text]
  (condp re-matches text

    #"^\-slacky.*" (fn [{:keys [channel]}] (tx/say-message channel "Hello, I'm here"))

    #"^\-meme .*" (fn [{:keys [channel]}] (tx/say-message channel "One does not simply meme before the functionality is ready"))

    nil))

(defn handle-message
  "translates a slack message into a command, handles that command, and communicates the reply"
  [{channel-id :channel, text :text, :as msg}]
  (try
    (when-let [command (find-command text)]
      (command msg))
    (catch Exception ex
      (clojure.repl/pst (Exception. (str "Exception trying to handle slack message\n" (str msg) ".") ex))
      (try (tx/say-message channel-id "You broke me. Check my logs for the exception")))))


(defn dispatch-handle-slack-event [event] ((juxt :type :subtype) event))

(defmulti handle-slack-event #'dispatch-handle-slack-event)

(defmethod handle-slack-event ["message" nil]
  [{user-id :user, :as msg}]
  (when (not (state/bot? user-id))
    (handle-message msg)))

(defmethod handle-slack-event ["message" "message_changed"]
  [event]
  nil)

(defmethod handle-slack-event ["channel_joined" nil]
  [event]
  nil)

(defmethod handle-slack-event :default
  [event]
  nil)


(defn try-handle-slack-event
  [event]
  (try
    (handle-slack-event event)
    (catch Exception ex
      (printex (str "Exception trying to handle slack event\n" (str event) ".") ex))))


(defn wait-for-console-quit []
  (loop []
    (let [input (read-line)]
      (when-not (= input "q")
        (recur)))))


(defn shutdown-app []
  (slack/disconnect)
  (println "...slacky dying"))


(defn stop []
  (shutdown-app))

(defn start
  ([]
   (start (slurp "api-token.txt")))
  ([api-token]
   (try
     (alter-var-root (var *api-token*) (constantly api-token))
     (slack/connect *api-token* try-handle-slack-event)
     (println "slacky is running")
     (catch Exception ex
       (println ex)
       (println "couldn't start slacky")
       (stop)))))

(defn restart []
  (stop)
  (start))

(defn -main
  [& args]
  (try
    (start)
    (wait-for-console-quit)
    (finally
      (stop)
      (shutdown-agents))))
