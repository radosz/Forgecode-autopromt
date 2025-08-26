import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * ForgeCode-specific task processor for handling tmux pane output and task management
 */
class ForgeCodeTaskProcessorImpl {

    final String PROMPT_PREFIX = "❯"
    final String ACCEPT_PERM = "[Use arrow keys to navigate, Enter to select, ESC to cancel]"
    final String CONTINUE_CONV="? Start a new conversation? (Y/n)"

    /**
     * Process the captured tmux pane output and handle task execution
     *
     * @param sessionName - tmux session identifier
     * @param lastLine - last line captured from tmux pane
     * @param taskLines - stack of remaining tasks
     * @param taskNumb - current task number
     * @param taskSize - total number of tasks
     * @param alreadyPrinted - flag to prevent duplicate printing
     * @param sendTaskToTmux - closure for sending task to tmux
     * @param captureTmuxPane - closure for capturing tmux pane
     * @return boolean - true to continue processing, false to stop
     */
    boolean processOutput(String sessionName, String lastLine, Stack taskLines,
                          AtomicInteger taskNumb, int taskSize, AtomicBoolean alreadyPrinted,
                          Closure sendTaskToTmux, Closure captureTmuxPane, Closure sendKeysToTmux) {

        switch (lastLine){
            case "Ok to proceed? (y)":
            case "? Do you want to continue anyway? (Y/n)":
                sendKeysToTmux("y", "Enter")
        }

        if (lastLine.startsWith(ACCEPT_PERM)) {
            sendKeysToTmux("Enter")
        }

        if (lastLine.startsWith(PROMPT_PREFIX)) {
            if (taskLines.size() > 0) {
                def task = taskLines.pop()
                println "\n[${nowStr()}] Processing task: ${taskNumb.get()} [${taskNumb.get()}/$taskSize]"
                println "=============================="
                println "$task"
                println "=============================="
                sendTaskToTmux(task.replaceAll("\n", "  ")) //new line is not acceptable for forge
                taskNumb.incrementAndGet()
                return true

            }
        }
        if (lastLine.startsWith(CONTINUE_CONV)) {
            sendKeysToTmux("n", "Enter") 
        }
        return true // Continue processing
    }

    private static String nowStr() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
    }
}

// Return the class so it can be instantiated
return ForgeCodeTaskProcessorImpl
