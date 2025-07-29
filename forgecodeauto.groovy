#!/usr/bin/env groovy
@GrabResolver(name = 'jitpack', root = 'https://jitpack.io')
@Grab('com.github.radosz:TmuxRunner:main-SNAPSHOT')

import groovy.transform.Field

/**
 * ForgeCode Auto - Task automation using TmuxRunner library from JitPack
 *
 * Dependencies:
 *   - TmuxRunner library loaded from GitHub via JitPack.io
 *
 * Usage: groovy forgecodeauto.groovy <tasks_file>
 */

// === FORGE CONFIGURATION - Easy to edit ===
@Field forgeConfig = '''\
model: anthropic/claude-sonnet-4
max_walker_depth: 1
top_p: 0.8
top_k: 30
max_tokens: 20480
max_tool_failure_per_turn: 5000
max_requests_per_turn: 5000
'''

// Script constants
@Field TASK_DELIMITER = "=END="
@Field FORGECODE_COMMAND = "npx forgecode@latest"

// Create forge.yaml configuration file in current working directory
def createForgeConfig() {
    def forgeFile = new File('forge.yaml')
    forgeFile.text = forgeConfig
    println "Created/updated forge.yaml in ${System.getProperty('user.dir')}"
}

@SuppressWarnings('GroovyUnusedAssignment')
def parseTasks(tasksFile) {
    def taskFile = new File(tasksFile)
    if (!taskFile.exists()) {
        throw new FileNotFoundException("Tasks file '${tasksFile}' does not exist!")
    }

    def fileContent = taskFile.text
    def blocks = fileContent.split(TASK_DELIMITER)
        .collect { it.trim() }
        .findAll { it }

    if (blocks.isEmpty()) {
        throw new IllegalArgumentException("No valid tasks found in '${tasksFile}'!")
    }

    def pre = ""
    if (blocks[0].startsWith("=PRE=")) {
        pre = blocks[0].substring(5).trim()
        blocks = blocks.drop(1)
    }

    def tasks = []
    if (pre) {
        tasks = blocks.collect { "${pre}\n${it}" }
    } else {
        tasks = blocks
    }

    // Stack (LIFO) - reverse order so first task is at top
    def taskLines = new Stack()
    tasks.reverse().each { taskLines.push(it) }

    println "Parsed ${taskLines.size()} tasks from '${tasksFile}'"
    return taskLines
}

// Check arguments
if (args.length == 0) {
    println "Usage: groovy forgecodeauto.groovy [-save-session] [-attach=session_id] <tasks_file>"
    println "Example: groovy forgecodeauto.groovy /Users/radoslav/Projects/test-forgeauto/promts.txt"
    println "Example: groovy forgecodeauto.groovy -save-session /Users/radoslav/Projects/test-forgeauto/promts.txt"
    println "Example: groovy forgecodeauto.groovy -attach=mysession /Users/radoslav/Projects/test-forgeauto/promts.txt"
    System.exit(1)
}

try {
    // Create forge.yaml configuration first
    createForgeConfig()
    
// Parse command line flags
    def saveSessionFlag = false
    def attachSessionId = null
    def filteredArgs = []
    
    args.each { arg -> 
        if (arg == "-save-session") {
            saveSessionFlag = true
        } else if (arg.startsWith("-attach=")) {
            saveSessionFlag = true
            attachSessionId = arg.substring(8) // Remove "-attach=" prefix
        } else {
            filteredArgs << arg
        }
    }
    
    if (filteredArgs.isEmpty()) {
        println "Error: No tasks file specified!"
        println "Usage: groovy forgecodeauto.groovy [-save-session] [-attach=session_id] <tasks_file>"
        System.exit(1)
    }
    
    // Parse tasks
    def tasksFile = filteredArgs[0]
    def taskLines = parseTasks(tasksFile)

    def runner = attachSessionId ?
        new TmuxRunnerImpl(FORGECODE_COMMAND, "forgecode", attachSessionId) :
        new TmuxRunnerImpl(FORGECODE_COMMAND, "forgecode")
    println "TmuxRunner loaded successfully: ${runner.class.name}"
    
    // Configure save-session behavior using closure to override killTmuxSession method
    if (saveSessionFlag) {
        println "Save-session flag detected - tmux session will be preserved after completion"
        // Override the killTmuxSession method using metaclass to prevent session termination
        runner.metaClass.killTmuxSession = {
            println "Session preservation enabled - skipping tmux session termination"
            println "Tmux session '${runner.sessionName}' is still running"
            println "To manually kill the session later, run: tmux kill-session -t ${runner.sessionName}"
            return 0 // Return success code without actually killing the session
        }
    }
    
    // Get the directory where this script is located
    def scriptFile = new File(getClass().protectionDomain.codeSource.location.toURI())
    def scriptDir = scriptFile.parent
    
    // Load processors from script directory
    println "Loading processors from: ${scriptDir}"
    runner.loadProcessors(new File(scriptDir))
    
    // Start the runner
    runner.start(taskLines, taskLines.size())
    
    // Wait for completion
    runner.waitForCompletion()
    
} catch (Exception e) {
    e.printStackTrace()
    System.exit(1)
}
