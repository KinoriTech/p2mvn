package tech.kinori.eclipse.p2mvn

import com.diogonunes.jcolor.Ansi.colorize
import com.diogonunes.jcolor.Attribute.*
import org.asynchttpclient.Dsl
import tech.kinori.eclipse.p2mvn.cli.Message
import tech.kinori.eclipse.p2mvn.maven.Mode
import tech.kinori.eclipse.p2mvn.p2.Repository
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess

val message = Message()

fun main(args: Array<String>) {
    println("\n" +
            "         ____                                 \n" +
            "    _ __|___ \\    /\\/\\   __ ___   _____ _ __  \n" +
            "   | '_ \\ __) |  /    \\ / _` \\ \\ / / _ \\ '_ \\ \n" +
            "   | |_) / __/  / /\\/\\ \\ (_| |\\ V /  __/ | | |\n" +
            "   | .__/_____| \\/    \\/\\__,_| \\_/ \\___|_| |_|\n" +
            "   |_|                                        \n")
    println("")
    println("Welcome to p2 Maven")
    // Get the home dir
    val p2mvnFolder = Paths.get(System.getProperty("user.home"), "p2mvn")
    message.showResult("Downloaded jars and maven scripts (per group id) will be stored in folder", p2mvnFolder.toString())
    message.showWarn("All the folder contents will be deleted.")
    val line = colorize("-".repeat(100))
    println(line)
    println()
    println(colorize("If deploying to a public repository, make sure you have the right/permission to publish the p2 content.", RED_TEXT()))
    println()
    println(line)

    message.askInput("What p2 repository are you exporting to maven")
    val p2Url = readLine()
    var mode = Mode.INVALID
    message.askInput("Do you want to install locally (l) or deploy (d) the p2 jars?", "(L/d)")
    mode = Mode.fromParam(readLine())
    while (mode == Mode.INVALID) {
        message.askInput("You must chose install local (l) or deploy (d)", "(L/d)")
        mode = Mode.fromParam(readLine())
    }
    var repoUrl: String? = null
    var repoId: String? = null
    if (mode == Mode.DEPLOY) {
        message.askInput("What is the maven repository url (must accept snapshot versions)")
        repoUrl = readLine()
        message.askInput("What is the maven repository id (as defined in the .m2 settings)")
        repoId = readLine()
    }
    val client = Dsl.asyncHttpClient()
    var inspector = Inspector(p2Url, client)
    var getInfo = true;
    var download = false;
    var repo: Repository? = null;
    while (getInfo) {
        try {
            repo = inspector.analyze()
            if (repo.isComposite) {
                message.showInfo("Found composite p2 repository")
                message.askInput("${repo.size()} repositories will be processed, continue", "y/N")
                var accept = readLine()
                if ("" == accept) {
                    accept = "y"
                }
                download =  "y" == accept || "Y" == accept
            } else {
                message.showInfo("Found single p2 repository")
                download = true;
            }
            getInfo = false
        } catch (e1: IllegalArgumentException) {
            // Check url
            e1.message?.let { message.showError(it) };
            client.close();
            exitProcess(1);
        } catch (e2: P2Exception) {
            e2.message?.let { message.showWarn(it) };
            message.askInput("Unable to connect to repository, try again", "y/N")
            var accept = readLine()
            if ("".equals(accept)) {
                accept = "y"
            }
            getInfo = "y" == accept || "Y" == accept
        }
    }
    if (repo != null && download) {
        try {
            Files.createDirectories(p2mvnFolder)
            repo.process(client, p2mvnFolder, mode, repoId, repoUrl)
        } catch (e2: P2Exception) {
            e2.message?.let { message.showError(it) };
        } catch (e: IOException) {
            message.showError("Unable to create p2mvn folder in user home; " + e.message)
        }
    }
    client.close();
    message.showInfo("Head to the p2mvn folder and run the generated scripts!")
    message.showProgress("Finished.")
}



