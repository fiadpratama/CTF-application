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
        "Through this challenge, you navigated simulated real-world mobile security vulnerabilities " +
        "spanning concealed native binary assets, custom application protocol inspection, " +
        "and stateful multi-step session verification.\n\n" +
        "These exercises reflect practical assessment scenarios aligned with the OWASP Mobile Application " +
        "Security Verification Standard (MASVS) and Testing Guide (MASTG).\n\n" +
        "ASSESSMENT DOMAINS\n" +
        "Binary & Native Artifact Analysis\n" +
        "Cryptographic & Protocol Reconstruction\n" +
        "Stateful Session & Token Verification\n\n" +
        "DEVELOPER & ARCHITECT\nFiad Pratama\n\n" +
        "CLIENT ARCHITECTURE\nAndroid SDK, Java & Native C++ (JNI/NDK)\n\n" +
        "BACKEND INFRASTRUCTURE\nNode.js, Express, Upstash Redis & Vercel Serverless\n\n" +
        "SECURITY STANDARDS\nOWASP MASVS & OWASP MASTG\n\n" +
        "BACKGROUND SCORE\nSignal to Noise by Scott Buckley\nLicensed under CC BY 4.0\nwww.scottbuckley.com.au\n\n" +
        "COMMUNITY CONDUCT\n" +
        "Thank you for participating. If you publish a write-up, please consider omitting direct solutions " +
        "and flags to preserve the challenge for future participants.\n\n" +
        "DEDICATION\n" +
        "To every security researcher, reverse engineer, and curious mind who persisted " +
        "through the noise to uncover the signal.\n\n" +
        "SESSION TERMINATED\n\n" +
        "FLAG ACQUIRED\n" + flag;
}

module.exports = {
    buildHallOfFameMessage
};
