global = window
b2Vec2 = Box2D.Common.Math.b2Vec2
b2AABB = Box2D.Collision.b2AABB
{b2BodyDef, b2Body, b2FixtureDef, b2Fixture, b2World, b2DebugDraw} = Box2D.Dynamics
{b2MassData, b2PolygonShape, b2CircleShape} = Box2D.Collision.Shapes

getCanvas = ->
    c = $('#canvas')
    global.W = c.width()
    global.H = c.height()
    return c[0]

v = (x, y) -> new b2Vec2(x, y)

createFixture = (shape) ->
    f = new b2FixtureDef
    f.density = 3.0
    f.friction = .3
    f.restitution = .7
    f.shape = shape if shape?
    f.filter.groupIndex = 1
    return f

createBody = (x, y) ->
    b = new b2BodyDef
    b.linearDamping = 0.05
    b.angularDamping = 0.1
    b.position.Set x, y if x? and y?
    b.type = b2Body.b2_dynamicBody
    return b

class Game
    scale: 30.0

    constructor: (@canvas) ->
        @centerX = global.W / (2 * @scale)
        @centerY = global.H / (2 * @scale)
        @toDestroy = []
        @world = null

    destroyElements: ->
        for b in @toDestroy
            data = b.GetUserData()
            @world.DestroyBody b
        @toDestroy = []

    animateWorld: ->
        @buildWorld()
        debugDraw = new b2DebugDraw()
        debugDraw.SetSprite(@canvas.getContext("2d"))
        debugDraw.SetDrawScale @scale
        debugDraw.SetFillAlpha(.7)
        debugDraw.SetLineThickness(1.0)
        debugDraw.SetFlags(b2DebugDraw.e_shapeBit | b2DebugDraw.e_jointBit)
        @world.SetDebugDraw(debugDraw)
        setInterval((=> @tick()), 1000 / 30)

    tick: ->
        @destroyElements()
        @maybeCreateElement()
        @world.Step(1 / 30, 10, 10)
        @world.DrawDebugData()
        @world.ClearForces()

    maybeCreateElement: ->
        return unless Math.random() <= .05
        randomY = (0.2 + 0.4 * Math.random())*  H / @scale
        @createCircle(Math.random() * W / @scale, randomY)

    createCircle: (x, y) ->
        f = createFixture(new b2CircleShape(0.5))
        b = createBody(x, y)
        @create b, f

    buildWorld: ->
        gravity = v(0, 10)
        doSleep = off
        @world = new b2World gravity, doSleep
        @buildWalls()
        @boxAt [2, 2], [3, 3]
        @boxAt [.5, .5], [6, 6]
        @triangleAt [15, 5]
        return

    buildWalls: ->
        w = W / (2 * @scale)
        h = H / (2 * @scale)
        dim = 200 / @scale
        @wall [w, dim], [w, -dim]
        @wall [w, dim], [w, 2 * h + dim]
        @wall [dim, h], [-dim, h]
        @wall [dim, h], [2 * w + dim, h]

    create: (body, fixture) ->
        body = @world.CreateBody(body)
        body.CreateFixture(fixture)
        return body

    triangleAt: (position) ->
        fixDef = createFixture(new b2PolygonShape())
        fixDef.shape.SetAsArray([v(-1, 0), v(1, 0), v(0, 2.0)])
        bodyDef = createBody position...
        @create bodyDef, fixDef


    boxAt: (dimensions, position) ->
        fixDef = createFixture(new b2PolygonShape())
        bodyDef = createBody(position...)
        fixDef.shape.SetAsBox dimensions...
        @create bodyDef, fixDef


    wall: (dimensions, position) ->
        fixDef = createFixture(new b2PolygonShape())
        fixDef.shape.SetAsBox dimensions...
        bodyDef = createBody(position...)
        bodyDef.type = b2Body.b2_staticBody
        @create bodyDef, fixDef


    getBodyAt: (x, y)->
        mousePVec = v(x, y)
        aabb = new b2AABB()
        aabb.lowerBound.Set(x - 0.001, y - 0.001)
        aabb.upperBound.Set(x + 0.001, y + 0.001)
        selectedBody = null
        callback = (f) ->
            return true if f.GetBody().GetType() == b2Body.b2_staticBody
            return true unless f.GetShape().TestPoint(f.GetBody().GetTransform(), mousePVec)
            selectedBody = f.GetBody()
            return false
        @world.QueryAABB callback, aabb
        return selectedBody


    deleteAt: (x, y) ->
        body = @getBodyAt(x, y)
        return unless body?
        @toDestroy.push body

    onClick: (x, y) ->
        @deleteAt x / @scale, y / @scale



init_web_app = ->
    canvas = getCanvas()
    game = new Game(canvas)
    $('#canvas').click (e) ->
        o = $(@).offset()
        game.onClick(e.pageX - o.left, e.pageY - o.top)
    game.animateWorld()
