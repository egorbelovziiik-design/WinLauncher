package nexus.api;

/** Video / game options bridge. All setters restore the previous value on disable. */
public interface IOptions {

    double gamma();

    void setGamma(double value);

    double fov();

    void setFov(double value);

    int renderDistance();

    void setRenderDistance(int value);

    int guiScale();

    boolean smoothLighting();

    boolean viewBobbing();

    void setViewBobbing(boolean value);

    boolean entityShadows();

    void setEntityShadows(boolean value);

    boolean cloudRenderMode();

    /** Current FPS counter reported by Minecraft itself. */
    int currentFps();

    boolean hideGui();

    boolean debugEnabled();

    float soundVolumeMaster();

    void setSoundVolumeMaster(float value);
}
