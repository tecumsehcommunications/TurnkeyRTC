(ns client.core
  (:require [client.repl :as repl]))

(defn ^:export main [ scene ]


(def loginPage (.getElementById js.document "loginPage"))
(def callPage (.getElementById js.document "callPage"))
(def rtcp-configuration (js-obj ("iceServers" (array (js-obj "url" "stun:stun2.1.google.com:19302")))))
(def usernameInput (.querySelector js.document "#usernameInput")) 
(def loginBtn (.querySelector js.document "#loginBtn")) 
(def callBtn (.getElementById js.document "callBtn"))
(def callPage (.querySelector js.document "#callPage")) 
(def callToUsername (.getElementById js.document "callToUsername"))
(def hangUpBtn (.querySelector js.document "#hangUpBtn"))
(def msgInput (.querySelector js.document "#msgInput"))
(def sendMsgBtn (.querySelector js.document "#sendMsgBtn"))
(def chatArea (.querySelector js.document "#chatarea"))
(def localAudio (.querySelector js.document "#localAudio"))
(def remoteAudio (.querySelector js.document "#remoteAudio"))

(def username )
(def connectedUser )
(def thisconn )
(def stream )

(def conn (new js.WebSocket "wss://192.168.248.25/ws"))


(set! conn.onopen #(js.console.log "connected to signalling server"))
(set! conn.onerror #(js.console.log "connection error: " %))

(set! conn.onmessage (fn [ msg ]
    (js.console.log "got message: " msg.data)
    (let [ data (.parse js.JSON msg.data) ]
      (case data.type

        "login"      (handleLogin data.success)
        "offer"      (handleOffer data.offer data.name)
        "answer"     (handleAnswer data.answer)
        "candidate"  (handleCandidate data.candidate)
        "leave"      (handleLeave)
        "default"))))

(defn send [ msg ]
  (if connectedUser (set! msg.name connectedUser))
  (.send conn (js.JSON.stringify msg)))


(set! loginBtn.onclick (fn [evt]
    (if (> usernameInput.value.length 0)
        (do                   
          (set! username usernameInput.value)
          (send (js-obj "type" "login"
                  "name" usernameInput.value ))))))
                                                  
(defn handleLogin [success]
  (if-not success
    (do
      (js.alert "user name did not work")
      (set! username nil))
    (do
      (set! loginPage.style.display "none")
      (set! callPage.style.display "block")

      (.catch
       (.then
        (.getUserMedia js.navigator.mediaDevices #js { :video false :audio true })
       (fn [astream]
        (def stream astream)
        (set! localAudio.srcObject stream ); (js.window.URL.createObjectURL stream))   
        (def thisconn (new js.RTCPeerConnection rtcp-configuration))
        (.addStream thisconn stream)                                                           
        (set! thisconn.onaddstream (fn [e] (set! remoteAudio.srcObject e.stream)))
                                                                        
 (comment ; this is for data channel setup     
      (set! thisconn.ondatachannel (fn [evt]
           (js.console.log "data channel created")
           (set! evt.channel.onopen (fn [oevt] ; the listeners must be set here in the callback, not seperately
               (set! oevt.target.onerror (fn [err]
                                  (js.console.log "datachannel error: " err)))
               (set! oevt.target.onmessage (fn [ msg ]
                                  (let [ oldText chatArea.innerHTML ]
                                  (set! chatArea.innerHTML (str oldText connectedUser ": " msg.data "<br />")))))
               (set! oevt.target.onclose (fn [ ]
                                           (js.console.log "data channel is closed"))))))))
                                                                   
; ice candidate resolution
      (set! thisconn.onicecandidate
            (fn [evt]
              (if evt.candidate
                (send (js-obj "type" "candidate"
                              "candidate" evt.candidate)))))))

   (fn [error] (js.console.log error))))))
; data channel creation

;  (def  dataChannel (.createDataChannel thisconn "channel1" (js-obj "reliable" true)))

      

(set! callBtn.onclick (fn [evt]
  (if (> callToUsername.value.length 0)
    (do
      (set! connectedUser callToUsername.value)
      (.createOffer thisconn
                   (fn [ offer ]
                     (send (js-obj "type" "offer"
                                   "offer" offer))
                     (.setLocalDescription thisconn offer))
                   (fn [ error ]
                     (js.console.log "error when creating offer")))))))

(defn handleOffer [ offer name ]
  (set! connectedUser name)
  (.setRemoteDescription thisconn (new js.RTCSessionDescription offer))
  (.createAnswer thisconn (fn [answer]
     (.setLocalDescription thisconn answer)
                            (send (js-obj "type" "answer"
                                          "answer" answer)))
                 (fn [error] (js.console.log "error when creating answer"))))

(defn handleAnswer [answer]
  (.setRemoteDescription thisconn (new js.RTCSessionDescription answer)))

(defn handleCandidate [candidate]
  (.addIceCandidate thisconn (new js.RTCIceCandidate candidate)))

(defn handleLeave []
  (set! connectedUser nil)
  (set! remoteAudio.src nil)
  (set! thisconn.onaddstream nil)
  (.close thisconn)
  (set! thisconn.onicecandidate nil))

(comment
(set! sendMsgBtn.onclick (fn [evt]
                           (let [ oldText chatArea.innerHTML
                                   msg msgInput.value ]
                             (set! chatArea.innerHTML (str oldText username ": " msg "<br />"))
                             (.send dataChannel msg)
                             (set! msgInput.value ""))))
)
(set! hangUpBtn.onclick (fn [evt]
                          (send (js-obj "type" "leave"))
                          (handleLeave)))


)
