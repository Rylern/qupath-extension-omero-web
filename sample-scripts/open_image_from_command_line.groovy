import qupath.ext.omero.imagesserver.*

/**
 * This script opens an image through the command line.
 */

/*
The script must be launched with the following command:

path/to/QuPath/bin/QuPath script \
    --image=the_web_link_of_your_image \
    --server "[--username, your_username, --password, your_password, --pixelAPI, Web]" \
    path/to/this/script/open_image_from_command_line.groovy

--server "[...]" is optional.
If you omit the credentials ([--username, your_username, --password, your_password]), they will be prompted (if necessary).
The parameters [--pixelAPI, Web] are used to specify QuPath how to retrieve pixel values. "Web" can be replaced by "Ice"
or "Pixel Buffer Microservice" (see the README file of the extension, "Reading images" section). If you omit these parameters,
the most accurate available pixel API will be selected.
If you select the web API ([--pixelAPI, Web]), you can add the [--jpegQuality, 0.6] optional argument to set the quality
level of the JPEG images returned by the web pixel API (number between 0 and 1).
If you select the pixel buffer microservice API ([--pixelAPI, Pixel Buffer Microservice]), you can add the
[--msPixelBufferPort, 8082] optional argument to set the port used by this microservice on the OMERO server.
 */

// Open server
def imageData = getCurrentImageData()
if (imageData == null) {
    println "Image not found"
    return
}
def server = imageData.getServer()
def omeroServer = (OmeroImageServer) server

// Print server type
println omeroServer.getServerType()

// Perform operations with image
// ...

// Close server
omeroServer.close()