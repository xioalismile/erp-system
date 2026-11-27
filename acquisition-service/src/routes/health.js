const express = require('express');

const router = express.Router();

/**
 * GET /api/health
 * 健康检查
 */
router.get('/', (req, res) => {
  res.json({
    status: 'ok',
    timestamp: Date.now()
  });
});

module.exports = router;
