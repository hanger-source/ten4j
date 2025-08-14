package source.hanger.server;

import source.hanger.server.controller.HelloWorldController;

public final class ServerConstants {
    private ServerConstants() {
        // Prevent instantiation
    }

    public static final String HTTP_CONTROLLER_PACKAGE = HelloWorldController.class.getPackage().getName();
}
