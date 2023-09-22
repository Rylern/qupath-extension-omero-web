package qupath.lib.images.servers.omero.web.entities.shapes;

import com.google.gson.annotations.SerializedName;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

/**
 * An ellipse.
 */
class Ellipse extends Shape {

    @SerializedName(value = "X", alternate = "x") private final double x;
    @SerializedName(value = "Y", alternate = "y") private final double y;
    @SerializedName(value = "RadiusX", alternate = "radiusX") private final double radiusX;
    @SerializedName(value = "RadiusY", alternate = "radiusY") private final double radiusY;

    /**
     * Creates an ellipse.
     *
     * @param x  x-coordinate of the center of the ellipse
     * @param y  y-coordinate of the center of the ellipse
     * @param radiusX  radius along the x-axis
     * @param radiusY  radius along the y-axis
     */
    public Ellipse(double x, double y, double radiusX, double radiusY) {
        this.x = x;
        this.y = y;
        this.radiusX = radiusX;
        this.radiusY = radiusY;
    }

    @Override
    public ROI createROI() {
        return ROIs.createEllipseROI(x-radiusX, y-radiusY, radiusX*2, radiusY*2, getPlane());
    }

    @Override
    public String toString() {
        return String.format("Ellipse located at {x: %f, y: %f} of radius {x: %f, y: %f}", x, y, radiusX, radiusY);
    }
}