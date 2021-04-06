package tech.kinori.eclipse.p2mvn

import com.diogonunes.jcolor.Ansi.colorize
import com.diogonunes.jcolor.Attribute.*
import org.asynchttpclient.Dsl
import tech.kinori.eclipse.p2mvn.p2.Repository
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess

const val ANSI_RESET = "\u001B[0m"
const val ANSI_RED = "\u001B[31m"
val prompt: String = colorize("?", GREEN_TEXT())
val warn: String = colorize("!", YELLOW_TEXT())
val err: String = colorize("X", RED_TEXT())

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
    var info = colorize(p2mvnFolder.toString(), YELLOW_TEXT())
    println("Downloaded jars and maven scripts (per group id) will be stored in folder: $info")
    val line = colorize("-".repeat(100))
    println(line)
    println()
    println(colorize("If deploying to a public repository, make sure you have the right/permission to publish the p2 content.", RED_TEXT()))
    println()
    println(line)

    askInput("What p2 repository are you exporting to maven?")
    val p2Url = readLine()
    var mode = Mode.INVALID
    askInput("Do you want to install local (l) or deploy (d) the p2 jars?", "(L/d)")
    mode = Mode.fromParam(readLine())
    while (mode == Mode.INVALID) {
        askInput("You must chose install local (l) or deploy (d)", "(L/d)")
        mode = Mode.fromParam(readLine())
    }
    var repoUrl: String? = null
    var repoId: String? = null
    if (mode == Mode.DEPLOY) {
        askInput("What is the maven repository url (must accept snapshot versions)")
        repoUrl = readLine()
        askInput("What is the maven repository id (as defined in the .m2 settings)")
        repoId = readLine()
    }
    val client = Dsl.asyncHttpClient()

    var inspector = Inspector(p2Url, p2mvnFolder, repoId, repoUrl, client)
    var getInfo = true;
    var download = false;
    var repo: Repository? = null;
    while (getInfo) {
        try {
            repo = inspector.analyze()
            if (repo.isComposite) {
                println(colorize("Found composite p2 repository", CYAN_TEXT()))
                askInput("${repo.size()} repositories will be processed, continue", "y/N")
                var cont = readLine()
                download =  "y".equals(cont) || "Y".equals(cont)
            } else {
                println(colorize("Found single p2 repository", CYAN_TEXT()))
                download = true;
            }
            getInfo = false
        } catch (e1: IllegalArgumentException) {
            // Check url
            showError(e1.message);
            client.close();
            exitProcess(1);
        } catch (e2: IllegalArgumentException) {
            showWarn(e2.message);
            askInput("Unable to connect to repository, try again", "y/N")
            var again = readLine()
            getInfo = "y".equals(again) || "Y".equals(again)
        }
    }
    if (repo != null && download) {
        // For each coordinate, create the folder, pom and download jars
        Files.createDirectories(p2mvnFolder)
        showProgress("Downloading p2 repository jars.")
        repo.process(client, p2mvnFolder, mode, repoId, repoUrl)
    }
    client.close();
    showProgress("maven scripts created successfully.")
}


fun askInput(text: String, options: String = "") {
    val entry = colorize(text, MAGENTA_TEXT())
    val choices = colorize(options, YELLOW_TEXT())
    print("$prompt $entry $choices")
}

fun showProgress(text: String?) {
    val entry = colorize(text, GREEN_TEXT())
    println("$entry")
}

fun showError(text: String?) {
    val entry = colorize(text, RED_TEXT(), BOLD())
    println("$err $entry")
}

fun showWarn(text: String?) {
    val entry = colorize(text, YELLOW_TEXT(), BOLD())
    println("$warn $entry")
}
