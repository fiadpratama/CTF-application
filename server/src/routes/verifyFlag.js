/**
 * Stage 3: Flag redemption and victory debriefing.
 * 
 * Validates submitted candidate flags, records solve metrics atomically,
 * and compiles structured Hall of Fame debriefing output.
 */
const express = require('express');
const { claimSolveFlag } = require('../services/vaultState');
const { buildHallOfFameMessage } = require('../services/hallOfFame');

const router = express.Router();

router.post('/verify-flag', async (req, res) => {
    const { flag } = req.body || {};
    if (!flag || typeof flag !== 'string' || flag.length !== 38 || !/^FLAG\{[0-9a-f]{32}\}$/.test(flag)) {
        return res.status(400).json({ status: 400, error: "Bad Request", message: "Valid flag format required" });
    }

    const result = await claimSolveFlag(flag);

    if (result.status !== 200) {
        return res.status(result.status).json({
            status: result.status,
            error: result.error,
            message: result.message
        });
    }

    const solveNumber = result.solveNumber;

    return res.status(200).json({
        status: 200,
        success: true,
        title: "HALL OF FAME",
        solve_number: solveNumber,
        message: buildHallOfFameMessage(solveNumber, flag)
    });
});

module.exports = router;
