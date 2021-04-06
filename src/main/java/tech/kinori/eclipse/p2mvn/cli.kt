package tech.kinori.eclipse.p2mvn

import com.diogonunes.jcolor.Ansi.colorize
import com.diogonunes.jcolor.Attribute.*
import org.asynchttpclient.Dsl
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
    println("Downloaded jars and maven script will be stored in folder: $info")
    val line = colorize("-".repeat(100))
    println(line)
    println()
    println(colorize("If deploying to a public repository, make sure you have the right/permission to publish the p2 content.", RED_TEXT()))
    println()
    println(line)

    askInput("What p2 repository are you exporting to maven?")
    val p2Url = readLine()
    var mode = P2Repository.Mode.INVALID
    askInput("Do you want to install local (l) or deploy (d) the p2 jars?", "(L/d)")
    mode = P2Repository.Mode.fromParam(readLine())
    while (mode == P2Repository.Mode.INVALID) {
        askInput("You must chose install local (l) or deploy (d)", "(L/d)")
        mode = P2Repository.Mode.fromParam(readLine())
    }
    var repoUrl: String? = null
    var repoId: String? = null
    if (mode == P2Repository.Mode.DEPLOY) {
        askInput("What is the maven repository url (must accept snapshot versions)")
        repoUrl = readLine()
        askInput("What is the maven repository id (as defined in the .m2 settings)")
        repoId = readLine()
    }
    val client = Dsl.asyncHttpClient()

    var p2repo = P2Repository(p2Url, p2mvnFolder, repoId, repoUrl, client)
    var getInfo = true;
    while (getInfo) {
        try {
            val type = p2repo.analyze()
            when (type) {
                P2Repository.Type.COMPOSITE_JAR, P2Repository.Type.COMPOSITE -> println(colorize("p2 repo is composite", CYAN_TEXT()))
                P2Repository.Type.SINGLE_JAR, P2Repository.Type.SINGLE -> println(colorize("p2 repo is single", CYAN_TEXT()))
            }
            getInfo = true
        } catch (e1: IllegalArgumentException) {
            // Check url
            showError(e1.message);
            client.close();
            exitProcess(1);
        } catch (e2: IllegalStateException) {
            showWarn(e2.message);
            askInput("Unable to connect to repository, try again", "y/N")
            var again = readLine()
            getInfo = "y".equals(again) || "Y".equals(again)
        }
    }

    client.close();
    showProgress("maven script created successfully.")
}

fun handleComposite() {

}

fun handleSingle() {

}

fun askInput(text: String, options: String = "") {
    val entry = colorize(text, MAGENTA_TEXT())
    val choices = colorize(options, YELLOW_TEXT())
    print("$prompt $entry $choices")
}

fun showProgress(text: String?) {
    val entry = colorize(text, GREEN_TEXT())
    print("$entry")
}

fun showError(text: String?) {
    val entry = colorize(text, RED_TEXT(), BOLD())
    print("$err $entry")
}

fun showWarn(text: String?) {
    val entry = colorize(text, YELLOW_TEXT(), BOLD())
    print("$warn $entry")
}
