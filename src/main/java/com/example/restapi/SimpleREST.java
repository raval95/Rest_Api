package com.example.restapi;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.mongo.HashAlgorithm;
import io.vertx.ext.auth.mongo.MongoAuth;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;


public class SimpleREST extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(SimpleREST.class);
    private final Router router;
    private final JWTAuth jwtAuth;
    private final MongoClient client;
    private final MongoAuth mongoAuth;

    public SimpleREST() {
        vertx = Vertx.vertx();
        router = Router.router(vertx);
        jwtAuth = getAuthProvider(vertx);
        client = MongoClient.create(vertx, new JsonObject());
        mongoAuth = getMongoAuth(client);
    }

    // Convenience method so you can run it in your IDE
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new SimpleREST());
    }

    @Override
    public void start() {
        mongoAuth.setHashAlgorithm(HashAlgorithm.PBKDF2);

        router.route().handler(BodyHandler.create());
        router.post("/login").handler(this::handleLogin);
        router.post("/register").handler(this::handleRegister);
        router.post("/items").handler(this::handleAddItems);
        router.get("/items").handler(this::handleListItems);

        vertx.createHttpServer().requestHandler(router).listen(8080);
    }

    private void handleLogin(RoutingContext routingContext) {
        JsonObject principal = routingContext.getBody().toJsonObject();

        JsonObject authInfo = new JsonObject()
                .put("username", principal.getString("login"))
                .put("password", principal.getString("password"));

        mongoAuth.authenticate(authInfo, res -> {
            if (res.succeeded()) {
                //create jwt token
                String token = jwtAuth.generateToken(new JsonObject().put("sub", res.result().principal().getString("_id")));

                routingContext.response().putHeader("Authentication", token).end();
            } else {
                logger.error("failed login", res.cause().getStackTrace());
                routingContext.fail(401);
            }
        });
    }

    private void handleRegister(RoutingContext routingContext) {
        JsonObject principal = routingContext.getBody().toJsonObject();
        mongoAuth.insertUser(principal.getString("login"), principal.getString("password"), null, null, res -> {
            if (res.succeeded()) {
                routingContext.response().setStatusCode(201).end();
            } else {
                //if user with same username exist in mongo it will fail
                routingContext.fail(500, res.cause());
            }
        });
    }

    private void handleListItems(RoutingContext routingContext) {
        String token = routingContext.request().headers().get("Authorization");
        jwtAuth.authenticate(new JsonObject().put("jwt", token), res -> {
            if (res.succeeded()) {
                User user = res.result();
                String id = user.principal().getString("sub");

                getItems(client, id, routingContext);
            } else {
                routingContext.fail(401);
            }
        });
    }

    private void handleAddItems(RoutingContext routingContext) {
        String token = routingContext.request().headers().get("Authorization");
        JsonObject item = routingContext.getBody().toJsonObject();

        jwtAuth.authenticate(new JsonObject().put("jwt", token), res -> {
            if (res.succeeded()) {
                item.put("owner", res.result().principal().getString("sub"));
                saveItem(client, item, routingContext);
            } else {
                routingContext.fail(401);
            }
        });
    }

    private void saveItem(MongoClient client, JsonObject item, RoutingContext routingContext) {
        client.save("items", item, r -> {
            if (r.succeeded()) {
                String id = r.result();
                System.out.println("Saved item with id " + id);
                routingContext.response().setStatusCode(201).end();
            } else {
                r.cause().printStackTrace();
                routingContext.fail(500);
            }
        });
    }

    private void getItems(MongoClient client, String id, RoutingContext routingContext) {
        JsonObject query = new JsonObject()
                .put("owner", id);

        client.find("items", query, res -> {
            if (res.succeeded()) {
                JsonObject result = new JsonObject();
                result.put("items", res.result());
                routingContext.response().end(result.encode());
            } else {
                res.cause().printStackTrace();
                routingContext.fail(500);
            }
        });
    }

    private MongoAuth getMongoAuth(MongoClient client) {
        JsonObject authProperties = new JsonObject();
        return MongoAuth.create(client, authProperties);
    }

    private JWTAuth getAuthProvider(Vertx vertx) {
        return JWTAuth.create(vertx, new JWTAuthOptions()
                .addPubSecKey(new PubSecKeyOptions()
                        .setAlgorithm("HS256")
                        .setPublicKey("keyboard cat")
                        .setSymmetric(true)));
    }
}
