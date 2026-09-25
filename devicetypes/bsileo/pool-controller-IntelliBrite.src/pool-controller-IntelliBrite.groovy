/**
 *  Pool Controller IntelliBrite Light
 *  Author: Brad Sileo / SMJ
 *  Namespace: bsileo
 *  Version: 1.1 SMJ
 */

metadata {
    definition (name: "Pool Controller IntelliBrite", namespace: "bsileo", author: "Brad Sileo / Qwen") {
        capability "Switch"
        capability "Light"
        
        attribute "lightingTheme", "string"
        attribute "themeSequence", "string"
        
         command "setTheme", [[name:"Theme*", type:"ENUM", description:"Lighting theme to set", 
            constraints:["white", "green", "blue", "magenta", "red", "sam", "party", "romance", "caribbean", "american", "sunset", "royal"]]]
        command "nextTheme"
    }

    preferences {         
        input (name: "configLoggingLevelIDE", title: "IDE Live Logging Level", type: "enum",
            options: ["None", "Error", "Warning", "Info", "Debug", "Trace"], defaultValue: "Info", required: false)
        input (name: "updateInterval", title: "Minimum interval between processing updates (seconds)", type: "enum",
            options: ["0", "1", "10", "30", "60"], defaultValue: "10", required: true)
    }
}

def installed() { state.loggingLevelIDE = (settings.configLoggingLevelIDE) ? settings.configLoggingLevelIDE : 'Info' }
def updated() { state.loggingLevelIDE = (settings.configLoggingLevelIDE) ? settings.configLoggingLevelIDE : 'Info' }

def on() {
    def id = getDataValue("circuitID")
    def body = [id: id.toInteger(), state: 1]
    logger("Turn on circuit ${id}","debug")
    sendPut("/state/circuit/setState", 'stateChangeCallback', body, null)
    sendEvent(name: "switch", value: "on", displayed:false, isStateChange:false)
}

def off() {
    def id = getDataValue("circuitID")
    def body = [id: id.toInteger(), state: 0]
    logger("Turn off circuit ${id}","debug")
    sendPut("/state/circuit/setState", 'stateChangeCallback', body, null)
    sendEvent(name: "switch", value: "off", displayed:false, isStateChange:false)
}

def setTheme(themeName) {
    def id = getDataValue("circuitID")
    if (themeName) {
        // Try sending the theme name instead of val
        def body = "{\"id\": ${id}, \"lightingTheme\": {\"name\": \"${themeName.toLowerCase()}\"}}"
        logger("Sending setTheme: ${body}","info")
        sendPut("/state/circuit/setTheme", 'themeChangeCallback', body, null)
        sendEvent(name: "lightingTheme", value: themeName, displayed:true, isStateChange:true)
    } else {
        logger("Unknown theme requested: ${themeName}","warn")
    }
}

def nextTheme() {
    def themes = ["white", "green", "blue", "magenta", "red", "sam", "party", "romance", "caribbean", "american", "sunset", "royal"]
    def current = device.currentValue("lightingTheme") ?: "white"
    def idx = themes.indexOf(current.toLowerCase())
    def nextIdx = (idx + 1) % themes.size()
    setTheme(themes[nextIdx])
}

// This is called by the main driver to push state updates
def parse(body) {
    if (body instanceof List) { body.each { parse(it) }; return }
    
    if (timeIntervalOK()) {
        logger("Parse IntelliBrite state - ${body}","trace")
        if (body.containsKey('isOn')) { 
            sendEvent([name: "switch", value: body.isOn ? "on" : "off", descriptionText: "Switch is ${body.isOn ? 'on' : 'off'}"]) 
        }
        if (body.containsKey('lightingTheme') && body.lightingTheme != null) {
            def theme = body.lightingTheme
            sendEvent([name: "lightingTheme", value: theme.name ?: "unknown", descriptionText: "Light theme is ${theme.name}"])
            if (theme.sequence != null) {
                sendEvent([name: "themeSequence", value: theme.sequence.toString(), descriptionText: "Theme sequence is ${theme.sequence}"])
            }
        }
    }
}

def stateChangeCallback(response, data) {
    logger("State Change Response ${response.getStatus() == 200 ? 'Success' : 'Failed'}","info")
}

def themeChangeCallback(response, data) {
    logger("Theme callback status: ${response.getStatus()}","info")
    if (response.getStatus() != 200) {
        logger("Theme change FAILED. Error: ${response.getErrorMessage()}","error")
    }
}

def getThemeValFromName(String name) {
    def themes = [
        "white": 0, "green": 1, "blue": 2, "magenta": 3, "red": 4, 
        "sam": 5, "party": 6, "romance": 7, "caribbean": 8, "american": 9, 
        "sunset": 10, "royal": 11
    ]
    return themes[name?.toLowerCase()]
}

def timeIntervalOK() {
    def delta = settings.updateInterval ? settings.updateInterval.toInteger() : 10
    if (delta == 0) return true
    def now = new Date().getTime()
    def lp = state.lastParse ? state.lastParse : 0
    if (now - lp > delta * 1000) { state.lastParse = now; return true }
    return false
}

private getHost() { return getParent().getHost() }
def getControllerURI() { return "http://${getHost()}" }

private sendPut(message, aCallback=generalCallback, body="", data=null) {
    def params = [uri: getControllerURI(), path: message, requestContentType: "application/json", contentType: "application/json", body: body]
    asynchttpPut(aCallback, params, data)
}

def generalCallback(response, data) { logger("Callback(status):${response.getStatus()}","debug") }

private logger(msg, level = "debug") {
    def lookup = ["None":0, "Error":1, "Warning":2, "Info":3, "Debug":4, "Trace":5]
    def logLevel = lookup[state.loggingLevelIDE ? state.loggingLevelIDE : 'Info']
    switch(level) {
        case "error": if (logLevel >= 1) log.error msg; break
        case "warn": if (logLevel >= 2) log.warn msg; break
        case "info": if (logLevel >= 3) log.info msg; break
        case "debug": if (logLevel >= 4) log.debug msg; break
        case "trace": if (logLevel >= 5) log.trace msg; break
        default: log.debug msg; break
    }
}
