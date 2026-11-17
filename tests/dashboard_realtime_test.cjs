const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const nodes = new Map();
function node(id) {
    if (!nodes.has(id)) nodes.set(id, {
        textContent: '', hidden: false, style: {}, dataset: {},
        classList: { add() {}, remove() {}, toggle() {} },
        querySelectorAll: () => [], appendChild() {}, addEventListener() {}
    });
    return nodes.get(id);
}
const handlers = {};
const context = {
    console, setTimeout: () => 0,
    document: {
        body: { dataset: { device: 'HELMET_001' }, appendChild() {} },
        getElementById: id => ['air-quality-chart', 'buzzer-form'].includes(id) ? null : node(id),
        querySelector: () => null, querySelectorAll: () => [],
        createElement: () => node(`created-${nodes.size}`)
    },
    location: { protocol: 'http:', host: 'localhost' },
    WebSocket: class { addEventListener(type, handler) { handlers[type] = handler; } },
};
context.window = { WebSocket: context.WebSocket, addEventListener() {} };
vm.runInNewContext(fs.readFileSync(require('node:path').join(__dirname,
    '../helmet-backend/src/main/resources/static/js/dashboard.js'), 'utf8'), context);
function receive(data) { handlers.message({ data: `MESSAGE\n\n${JSON.stringify(data)}\0` }); }
receive({ device_uid: 'HELMET_001', is_online: true, is_worn: true, gas_ppm: 160, gas_ready: true });
assert.equal(node('worn-stat').textContent, 'Đang đội mũ');
assert.equal(node('gas-stat').textContent, 160);
receive({ device_uid: 'HELMET_001', is_worn: false });
assert.equal(node('worn-stat').textContent, 'Chưa đội mũ');
receive({ deviceUid: 'HELMET_002', type: 'FALL', status: 'NEW', message: 'simulated', id: 2 });
assert.equal(node('fall-status').textContent, 'Bình thường');
receive({ deviceUid: 'HELMET_001', type: 'FALL', status: 'NEW', message: 'fall', id: 1 });
assert.equal(node('fall-status').textContent, 'Phát hiện té ngã');
receive({ deviceUid: 'HELMET_001', type: 'FALL', status: 'ACKNOWLEDGED', id: 1 });
assert.equal(node('fall-status').textContent, 'Bình thường');
assert.equal(node('status-online-text').textContent, 'Online');
receive({ gas_ready: false, gas_warmup_remaining: 32 });
assert.match(node('gas-label').textContent, /32s/);
assert.equal(node('gas-stat').textContent, '—');
console.log('Dashboard realtime: 9 assertions passed.');
