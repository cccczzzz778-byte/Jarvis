import http from 'node:http';

const PORT = Number(process.env.PORT || 8080);
const OPENAI_API_KEY = process.env.OPENAI_API_KEY || '';
const OPENAI_MODEL = process.env.OPENAI_MODEL || 'gpt-5.6-sol';

const schema = {
  type: 'object',
  additionalProperties: false,
  properties: {
    action: { type: 'string', enum: ['say','ask','done','tap','click_text','type','swipe','back','home','recents','open_app','send','delete','purchase','payment','call'] },
    speech: { type: 'string' },
    text: { type: 'string' },
    target: { type: 'string' },
    x: { type: 'number', minimum: 0, maximum: 1 },
    y: { type: 'number', minimum: 0, maximum: 1 },
    direction: { type: 'string', enum: ['', 'up','down','left','right'] },
    sensitive: { type: 'boolean' }
  },
  required: ['action','speech','text','target','x','y','direction','sensitive']
};

const instructions = `You are JARVIS, an Android phone UI agent. The user speaks Uzbek. Return exactly ONE next action, not a plan. Use visible screen text and screenshot together. Coordinates are normalized 0..1. Prefer click_text when a clear accessible label exists; use tap only when visual coordinates are necessary. Use swipe only when needed. If the task is complete, action=done. If information is missing, action=ask. Never enter, infer, expose, or request passwords, PINs, OTPs, CAPTCHAs, payment card data, or authentication secrets. Never bypass security warnings. Any send/call/delete/purchase/payment or other externally consequential action MUST use the corresponding action or set sensitive=true so the phone asks for explicit confirmation. Do not mark ordinary navigation, scrolling, opening an app, or typing an unsent draft as sensitive. Keep speech short and in Uzbek.`;

function send(res, status, body) {
  const text = JSON.stringify(body);
  res.writeHead(status, {'content-type':'application/json; charset=utf-8','content-length':Buffer.byteLength(text)});
  res.end(text);
}

async function handleAgent(payload) {
  if (!OPENAI_API_KEY) throw new Error('OPENAI_API_KEY is not configured');
  const contextText = [
    `USER REQUEST: ${String(payload.request || '').slice(0, 2000)}`,
    `ACTIVE PACKAGE: ${String(payload.package_name || '').slice(0, 250)}`,
    `VISIBLE ACCESSIBILITY TEXT: ${String(payload.screen_text || '').slice(0, 4000)}`,
    `LATEST NOTIFICATION: ${String(payload.notification_title || '').slice(0, 300)} | ${String(payload.notification_text || '').slice(0, 1000)}`
  ].join('\n');

  const content = [{type:'input_text', text:contextText}];
  if (payload.screenshot_base64) {
    content.push({type:'input_image', image_url:`data:image/jpeg;base64,${payload.screenshot_base64}`, detail:'low'});
  }

  const response = await fetch('https://api.openai.com/v1/responses', {
    method: 'POST',
    headers: {'authorization':`Bearer ${OPENAI_API_KEY}`,'content-type':'application/json'},
    body: JSON.stringify({
      model: OPENAI_MODEL,
      store: false,
      instructions,
      input: [{role:'user', content}],
      text: {format: {type:'json_schema', name:'phone_action', strict:true, schema}}
    })
  });
  const raw = await response.text();
  if (!response.ok) throw new Error(`OpenAI HTTP ${response.status}: ${raw.slice(0, 500)}`);
  const data = JSON.parse(raw);
  let output = data.output_text;
  if (!output && Array.isArray(data.output)) {
    for (const item of data.output) {
      for (const part of item.content || []) {
        if (part.type === 'output_text' && part.text) output = part.text;
      }
    }
  }
  if (!output) throw new Error('Model returned no output_text');
  return JSON.parse(output);
}

const server = http.createServer(async (req, res) => {
  if (req.method === 'GET' && req.url === '/health') return send(res, 200, {ok:true, model:OPENAI_MODEL, ai:Boolean(OPENAI_API_KEY)});
  if (req.method !== 'POST' || req.url !== '/v1/agent') return send(res, 404, {error:'not_found'});
  let body = '';
  req.on('data', chunk => {
    body += chunk;
    if (body.length > 5_500_000) req.destroy();
  });
  req.on('end', async () => {
    try {
      const payload = JSON.parse(body || '{}');
      const action = await handleAgent(payload);
      send(res, 200, action);
    } catch (e) {
      send(res, 503, {error:String(e?.message || e)});
    }
  });
});

server.listen(PORT, '0.0.0.0', () => console.log(`JARVIS v5 backend listening on :${PORT}`));
