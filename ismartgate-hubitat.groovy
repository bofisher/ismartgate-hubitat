/**
 *
 *  File: iSmartGateGarageControllerDriver.groovy
 *  Platform: Hubitat
 *
 *
 *  Requirements:
 *     1) iSmartGate Garage Controller connected to same LAN as your Hubitat Hub.  Use router
 *        DHCP Reservation to prevent IP address from changing.
 *     2) Authentication Credentials for iSmartGate Garage Door Open.  This is the credentials 
 *        that are used to log on to the opener at it's index.php
 *
 *  Original Copyright 2019 Robert B. Mergner
 *      https://github.com/bmergner/bcsmart
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Change History:
 *
 *    Date        Who            What
 *    ----        ---            ----
 *    2019-03-11  Bob Mergner  Original Creation
 *    2019-03-13  Bob Mergner  Cleaned up and refactored.  Added every two second polling for more reactive automation chains in rules
 *    2019-03-14  Bob Mergner  More cleanup
 *    2019-06-29  Shane Lord   Adjusted GoGoGate driver to work with the new iSmartGate and iSmartGate Pro
 *    2019-07-16  Shane Lord   Added capability to use door open/close sensor as Hubitat Contact Sensor
 *    2020-07-10  Bo Fisher   Heavy modification for firmware 1.6.3. For simpliity, removed all status functionality. Device only "toggles"
 *
 *    GoGoGate and iSmartGate are trademarks and/or copyrights of REMSOL EUROPE S.L. and its affiliates
 *    	 https://ismartgate.com
 */

def version() {"v1.1"}

import hubitat.helper.InterfaceUtils

metadata {
    definition (name: "iSmartGate Garage Controller v2", namespace: "stonewall", author: "Bo Fisher") {
        capability "Initialize"
        capability "Refresh"
	capability "DoorControl"
	//capability "Contact Sensor"		
	capability "Switch"	
	attribute "door", "string"
    }
}

preferences {
    input("ip", "text", title: "IP Address", description: "[IP Address of your iSmartGate Device]", required: true)
	input("user", "text", title: "iSmartGate Garage User", description: "[iSmartGate Username (usually admin)]", required: true)
	input("pass", "password", title: "iSmartGate Garage Password", description: "[Your iSmartGate user's Password]", required: true)
	input("door", "text", title: "Garage Door Number", description: "[Enter 1, 2 or 3]", required: true)
    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
}



def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def refresh() {
    log.info "refresh() called"
	initialize()
	
    GetDoorStatus()
}

def installed() {
    log.info "installed() called"
    updated()
}

def updated() {
    log.info "updated() called"
    //Unschedule any existing schedules
    unschedule()
    
    //Create a 30 minute timer for debug logging
    if (logEnable) runIn(1800,logsOff)
}

def doorPollStatus() {
    
}

def LogOnAndGetCookie(){

	def allcookie
	def cookie
    def tokens = []
	
	httpPost("http://${ip}", "login=${user}&pass=${pass}&send-login=Sign+In") { resp ->
		allcookie = resp.headers['Set-Cookie']
		//log.debug "SetCookieValue: ${allcookie}"
		
		cookie = allcookie.toString().replaceAll("; path=/","").replaceAll("Set-Cookie: ","")

        fullResp = resp.data.toString()
        tokenSplitter = 'webtoken'
        //log.debug "tokenSplitter: ${tokenSplitter}"

        //27 was the key here, 26 will pull back "="
        int tokenLocationStart = fullResp.indexOf(tokenSplitter) + 27
        //log.debug "tokenLocationStart: ${tokenLocationStart}"
        
        leftTrimResp = fullResp.substring(tokenLocationStart)
        //log.debug leftTrimResp

        
        fullTrimResp = leftTrimResp.substring(0, leftTrimResp.indexOf('/>'))
        log.debug fullTrimResp
    }
	
    tokens[0] = cookie
    tokens[1] = fullTrimResp
    
    log.debug "tokens: ${tokens}"
	return tokens
	
}

def GetDoorStatus() {
    def tokens = LogOnAndGetCookie()
    return tokens
}

def open() {
	log.info "Door ${door} received open command from Hubitat Elevation"

    def tokens = GetDoorStatus()

    toggleDoor(tokens)
}

def close() {
	log.info "Door ${door} received close command from Hubitat Elevation"
	
	def cookie = GetDoorStatus()

    toggleDoor(cookie)
}

def on() {
	log.info "Door ${door} received on command from Hubitat Elevation"

	def cookie = GetDoorStatus()

    toggleDoor(cookie)
}

def off() {
	log.info "Door ${door} received off command from Hubitat Elevation"

	def cookie = GetDoorStatus()

    toggleDoor(cookie)
}

def toggleDoor(tokens){
    
    log.debug "tokens: ${tokens}"
    
    sendUri = "http://${ip}/isg/opendoor.php?numdoor=${door}&status=0&webtoken=${tokens[1]}"
    log.info "uri: ${sendUri}"
   
    
	def params = [uri: sendUri,
		headers: ["Cookie": """${tokens[0]}""",
				  "Referer": "http://${ip}/index.php",
				  "Host": """${ip}""",
                  "Connection": "keep-alive"],
                  requestContentType: "application/json; charset=UTF-8"]

    try {
        httpGet(params) { resp ->
                log.debug resp.contentType
                log.debug resp.status
	    		log.debug resp.data
    
                fullResp = resp.data.toString()            
                log.debug fullResp
		    }
    }
    catch (groovyx.net.http.HttpResponseException respex) {
        log.debug "HTTP Exception: ${respex.getStatusCode()}"
        log.debug "Unforunately, on firmware 1.6.3, device responds with HTTP 500 during normal operation via loca API hack."
    }
}
	
def initialize() {
    log.info "initialize() called"
    
    if (!ip || !user || !pass || !door) {
        log.warn "iSmartGate Garage Door Controller required fields not completed.  Please complete for proper operation."
        return
    }
}
