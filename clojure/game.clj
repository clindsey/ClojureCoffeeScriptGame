(ns physicsGame)
(def jquery (js* "$"))
; Box2d Imports: Generated by macro.clj
(def b2AABB js/Box2D.Collision.b2AABB)
(def b2BodyDef js/Box2D.Dynamics.b2BodyDef)
(def b2Body js/Box2D.Dynamics.b2Body)
(def b2FixtureDef js/Box2D.Dynamics.b2FixtureDef)
(def b2Fixture js/Box2D.Dynamics.b2Fixture)
(def b2World js/Box2D.Dynamics.b2World)
(def b2DebugDraw js/Box2D.Dynamics.b2DebugDraw)
(def b2PolygonShape js/Box2D.Collision.Shapes.b2PolygonShape)
(def b2CircleShape js/Box2D.Collision.Shapes.b2CircleShape)

(defn- v [x y] (js/Box2D.Common.Math.b2Vec2. x y))
(defn- dom [s] (->> (name s) (str "#") jquery))

; Using set! is really verbose and doesn't accept anything other than bare symbols
(def nativejsset (js* "function (o, key ,val) {
   o[key] = val;
}"))

; Needed for triangle vertices
(defn- nativearray [& args]
  (let [ret (js/Array.)]
    (doseq [arg args] (.push ret arg))
    ret))

(defn- native-set-wrapper [jsobject attr value]
  (nativejsset jsobject (name attr) value)
  )
(defn- js-set
  "Sets an attribute name to a value on a javascript object
  Returns the original object"
  ([jsobject attr value]
    (do
      (native-set-wrapper jsobject attr value)
      jsobject
      )
    )
  ([jsobject & values]
    (do
      (doseq [[attr value] (apply hash-map values)]
        (native-set-wrapper jsobject attr value)
        )
      jsobject
      )
    )
  )

(defn- atom-set
  "Helper function for setting an atom of a map"
  [atom & values]
  (do
    (swap! atom #(apply assoc % values))
    atom
    )
  )

(def W)
(def H)
(def scale 30)
(def speed-rate 300)
(def empty-fn (constantly nil))


(defn- get-canvas []
  (let [canvas (dom :canvas)]
    (def W (. canvas (width)))
    (def H (. canvas (height)))
    (.get canvas 0)
    )
  )
;  (set! (. f density) 3) is ugly. Lack of macros/eval make this impossible to solve without native javascript
(defn- create-fixture
  ([shape] (js-set (b2FixtureDef.)
             :density 3
             :friction 0.3
             :restitution 0.9
             :shape shape
             ))
  ([] (create-fixture nil))
  )

(defn- create-body [x y]
  (let [b (b2BodyDef.)]
    (js-set b :type (.b2_dynamicBody b2Body))
    (-> (.position b) (.Set x y))
    b
    ))

(defn- create [game body-def fix-def]
  (let [body (-> (@game :world) (.CreateBody body-def))]
    (.CreateFixture body fix-def)
    body
    )
  )

(defn- wall
  ([game width height x y] (wall game width height x y nil))
  ([game width height x y user-data]
    (let [fix-def (create-fixture (b2PolygonShape.))
          body-def (create-body x y)]
      (-> (.shape fix-def) (.SetAsBox width height))
      (js-set body-def
        :userData user-data
        :type (.b2_staticBody b2Body))
      (create game body-def fix-def))
    )
  )

(defn- build-walls [game]
  (let [w (@game :center-x)
        h (@game :center-y)
        dim (/ 200 scale)]
    (wall game w dim w (- dim) :ceiling)
    (wall game w dim w (+ dim (* 2 h)))
    (wall game dim h (- dim) h)
    (wall game dim h (+ dim (* 2 w)) h)
    )
  )

(defn- random-body [x y]
  (let [b (create-body x y)
        vx (- (* 10 (rand)) 5)]
    (js-set b :angle (* 360 (rand))
      :linearVelocity (v vx 0)
      :angularVelocity (- (* 4 (rand)) 2)
      )
    )
  )

(defn- create-circle [game x y size]
  (create game (random-body x y) (create-fixture (b2CircleShape. size))))

(defn- create-square [game x y size]
  (let [fixture (create-fixture (b2PolygonShape.))]
    (.SetAsBox (.shape fixture) size size)
    (create game (random-body x y) fixture))
  )

(defn-  create-triangle [game x y size]
  (let [fixture (create-fixture (b2PolygonShape.))
        vertices (nativearray (v (- size) 0) (v size 0) (v 0 (* size (Math/sqrt 3))))]
    (.SetAsArray (.shape fixture) vertices)
    (create game (random-body x y) fixture))
  )

(defn- paused? [game] (@game :paused))
(defn- not-paused? [game] (not (paused? game)))
(defn- set-paused [game val] (atom-set game :paused val))


(defn- create-element [game]
  (let [randomY (/ (* H (+ 0.2 (* 0.4 (rand)))) scale)
        randomX (/ (+ 25 (* (rand) (- W 50))) scale)
        type (rand-nth [:circle :square :triangle])
        method (keyword (str "create-" (name type)))]
    ((@game method) game randomX randomY (inc (rand)))
    )
  )

(defn- maybe-create-element [game]
  (let [neg-probability (* 0.97 (Math/pow 0.95 (@game :speed)))]
    (if (> (rand) neg-probability) (create-element game))
    )
  )

(defn- update-speed [game]
  (let [domspeed (dom :speedValue)]
    (.text domspeed (@game :speed))
    (.. domspeed (hide) (slideDown))
    )
  )

(defn- update-score [game]
  (-> (dom :scoreValue) (.text (@game :score))))

(defn- increment-speed [game]
  (do
    (atom-set game :speed (inc (@game :speed)))
    (update-speed game)
    ))

(defn- destroy-elements [game]
  (let [to-destroy (@game :to-destroy)
        new-score (+ (@game :score) (count to-destroy))
        ]
    (doseq [b to-destroy] (-> (@game :world) (.DestroyBody b)))
    (atom-set game :to-destroy [], :score new-score)
    (update-score game)
    )
  )

(defn- do-tick [game]
  (let [w (@game :world)
        missing-ticks (@game :ticks-to-speed)]
    (if (= 1 missing-ticks)
      (do
        (atom-set game :ticks-to-speed speed-rate)
        (increment-speed game)
        )
      (atom-set game :ticks-to-speed (dec missing-ticks)))
    (if (not (empty? (@game :to-destroy))) (destroy-elements game))
    (maybe-create-element game)
    (.Step w (/ 1 30) 10 10)
    (. w (DrawDebugData))
    (. w (ClearForces))
    )
  )

(defn- tick [game]
  (if (and (@game :game-over) (not-paused? game))
    (do (set-paused game true)
      (. (dom :gameOver) (fadeIn)))
    (if (not-paused? game) (do-tick game))
    ))

(defn- animate-world [game]
  (let [debug-draw (b2DebugDraw.)]
    (doto debug-draw
      (.SetSprite (-> (@game :canvas) (.getContext "2d")))
      (.SetDrawScale scale)
      (.SetLineThickness 1.0)
      (.SetFlags (.e_shapeBit b2DebugDraw))
      )
    (-> (@game :world) (.SetDebugDraw debug-draw))
    (js/setInterval #(tick game) (/ 1000 30))
    ))


; Resolve doesn't work on clojurescript. Therefore, if we want to get a function from a string, we
; have to make the lookup ourselves.
(defn- add-methods [game-ref]
  (assoc game-ref
    :create-circle create-circle
    :create-square create-square
    :create-triangle create-triangle
    )
  )

(defn- init [game]
  (do
    (dotimes [_ 5] (create-element game))
    (atom-set game
      :to-destroy []
      :paused false
      :game-over false
      :score 0
      :speed 0
      :ticks-to-speed speed-rate
      ))
  )

(defn- pre-solve [game contact manifold]
  (if (. contact (IsTouching))
    (let [data-x (.. contact (GetFixtureA) (GetBody) (GetUserData))
          data-y (.. contact (GetFixtureB) (GetBody) (GetUserData))]
      (if (some #(= %1 :ceiling) [data-x data-y])
        (atom-set game :game-over true)
        )))
  )

(defn- contact-listener [game]
  (js-set (js/Object.)
    :PostSolve empty-fn
    :BeginContact empty-fn
    :EndContact empty-fn
    :PreSolve #(pre-solve game %1 %2)
    ))

(defn- set-contact-listener [game]
  (do
    (.SetContactListener (@game :world) (contact-listener game))
    game
    ))

(defn- build-world []
  (let [gravity (v 0 10)
        doSleep false
        world (b2World. gravity doSleep)]
    world
    )
  )

(defn- create-game [canvas]
  (let [twiceScale (* 2 scale)]
    (-> {:center-x (/ W twiceScale)
         :center-y (/ H twiceScale)
         :world (build-world)
         :canvas canvas
         }
      add-methods atom set-contact-listener init
      )
    ))

(defn- add-to-destroy [game body]
  (atom-set game :to-destroy (conj (@game :to-destroy) body))
  )

(defn- static-body? [body]
  (= (. body (GetType)) (.b2_staticBody b2Body)))
(defn- static? [fixture]
  (static-body? (. fixture (GetBody))))

(defn- not-has-point? [fixture vec]
  (let [shape (. fixture (GetShape))
        transform (.. fixture (GetBody) (GetTransform))]
    (not (.TestPoint shape transform vec))
    ))

(defn- delete-at [game x y]
  (let [mouse-vec (v x y)
        aabb (b2AABB.)
        delta 0.001
        callback (fn [f]
      (if (or (static? f) (not-has-point? f mouse-vec)) true
        (do (add-to-destroy game (. f (GetBody))) false)))]
    (-> (.lowerBound aabb) (.Set (- x delta) (- y delta)))
    (-> (.upperBound aabb) (.Set (+ x delta) (+ y delta)))
    (-> (@game :world) (.QueryAABB callback aabb))
    )
  )

(defn- on-click [game x y]
  (if (not-paused? game)
    (delete-at game (/ x scale) (/ y scale))))

(defn- update-pause-text [game]
  (-> (dom :pause)
    (.text (if (@game :paused) "Unpause" "Pause")
      )))

(defn- toggle-pause [game]
  (when-not (:game-over @game)
    (set-paused game (not-paused? game))
    (update-pause-text game)
    ))

(defn- iter-each-body [body func]
  (when body
    (func body)
    (iter-each-body (. body (GetNext)) func)
    ))

(defn- each-body [game func]
  (iter-each-body (. (@game :world) (GetBodyList)) func)
  )

(defn- cleanup-world [game]
  (each-body game
    #(when-not (static-body? %) (.DestroyBody (@game :world) %)))
  )

(defn- restart [game]
  (do
    (cleanup-world game)
    (init game)
    (. (dom :gameOver) (hide))
    (update-score game)
    (update-speed game)
    (update-pause-text game)
    )
  )

(defn init-web-app []
  (let [game (create-game (get-canvas))]
    (-> (dom :pause) (.click #(toggle-pause game)))
    (-> (dom :restart) (.click #(restart game)))
    (-> (dom :canvas) (.mousedown
                        (fn [e]
                          (let [o (. (dom :canvas) (offset))]
                            (on-click game (- (.pageX e) (.left o)) (- (.pageY e) (.top o)))
                            false
                            ))))
    (build-walls game)
    (animate-world game)
    ))

(jquery init-web-app)