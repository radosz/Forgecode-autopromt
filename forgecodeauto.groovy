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

// Parse tasks from file
def parseTasks(tasksFile) {
    def taskFile = new File(tasksFile)
    if (!taskFile.exists()) {
        throw new FileNotFoundException("Tasks file '${tasksFile}' does not exist!")
    }
    
    def fileContent = taskFile.text
    def taskLinesList = fileContent.split(TASK_DELIMITER)
        .collect { it.trim() }
        .findAll { it && !it.isEmpty() }
    
    if (taskLinesList.isEmpty()) {
        throw new IllegalArgumentException("No valid tasks found in '${tasksFile}'!")
    }
    
    // Convert to Stack (LIFO) - reverse order so first task is at top
    def taskLines = new Stack()
    taskLinesList.reverse().each { taskLines.push(it) }
    
    println "Parsed ${taskLines.size()} tasks from '${tasksFile}'"
    return taskLines
}

// Check arguments
if (args.length == 0) {
    println "Usage: groovy forgecodeauto.groovy <tasks_file>"
    println "Example: groovy forgecodeauto.groovy /Users/radoslav/Projects/test-forgeauto/promts.txt"
    System.exit(1)
}

try {
    // Create forge.yaml configuration first
    createForgeConfig()
    
    // Parse tasks
    def tasksFile = args[0]
    def taskLines = parseTasks(tasksFile)
    
    // Create TmuxRunner instance from JitPack dependency
    println "Initializing TmuxRunner from JitPack dependency..."
    def runner = new TmuxRunnerImpl(FORGECODE_COMMAND, "forgecode")
    println "TmuxRunner loaded successfully: ${runner.class.name}"
    
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
