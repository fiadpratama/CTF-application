/**
 * Hall of Fame debriefing service.
 * 
 * Constructs the structured victory debriefing narrative and solve telemetry.
 */

/**
 * Compiles the structured victory debriefing message displayed on the client.
 * 
 * @param {number|null} solveNumber - Global solve index counter
 * @param {string} flag - Verified flag string
 * @returns {string} Formatted debriefing message
 */
function buildHallOfFameMessage(solveNumber, flag) {
    const solveSection = solveNumber ? `SOLVE\n#${solveNumber}\n\n` : "";
    return "SECURITY ASSESSMENT COMPLETE\n\n" +
        "STATUS\nVULNERABILITY EXPLOITED\n\n" +
        "TARGET\nCTF APPLICATION CORE INFRASTRUCTURE\n\n" +
        solveSection +
        "You have successfully completed all three stages of this assessment.\n\n" +
        "Through this challenge, you navigated simulated real-world mobile security vulnerabilities: " +
        "discovering concealed assets embedded within compiled binaries, inspecting custom application protocols, " +
        "and carrying a multi-step session to completion.\n\n" +
        "These exercises reflect practical assessment scenarios aligned with the OWASP Mobile Application " +
        "Security Verification Standard (MASVS) and Testing Guide (MASTG).\n\n" +
        "Thank you for participating. If you publish a write-up, please consider omitting direct solutions " +
        "and flags to preserve the challenge for future participants.\n\n" +
        "DEVELOPER & ARCHITECT\nFiad Pratama\n\n" +
        "TECHNOLOGY STACK\nAndroid (Java/C++) & Node.js (Vercel Edge)\n\n" +
        "BACKGROUND SCORE\nW.A. Mozart - Dies Irae (Requiem in D minor)\n\n" +
        "SESSION TERMINATED\n\n" +
        "FLAG ACQUIRED\n" + flag;
}

module.exports = {
    buildHallOfFameMessage
};
