/**
 * WAV 编码工具（前端统一入口，避免多处重复实现）
 * 用途：把 Web Audio 采集到的 Float32 PCM 编码成 16 位单声道 WAV ——
 *   服务端自建识别（sherpa-onnx）直接吃 WAV，前端直接产 WAV 最准（无损 PCM、无中间有损编码），
 *   也因此服务端不必安装 ffmpeg（少一个组件、少一个故障点）。
 */
export function encodeWav (samples, sampleRate) {
  const buffer = new ArrayBuffer(44 + samples.length * 2)
  const view = new DataView(buffer)
  const writeStr = (off, s) => { for (let i = 0; i < s.length; i++) view.setUint8(off + i, s.charCodeAt(i)) }
  writeStr(0, 'RIFF'); view.setUint32(4, 36 + samples.length * 2, true); writeStr(8, 'WAVE')
  writeStr(12, 'fmt '); view.setUint32(16, 16, true); view.setUint16(20, 1, true); view.setUint16(22, 1, true)
  view.setUint32(24, sampleRate, true); view.setUint32(28, sampleRate * 2, true)
  view.setUint16(32, 2, true); view.setUint16(34, 16, true)
  writeStr(36, 'data'); view.setUint32(40, samples.length * 2, true)
  let off = 44
  for (let i = 0; i < samples.length; i++, off += 2) {
    const s = Math.max(-1, Math.min(1, samples[i]))
    view.setInt16(off, s < 0 ? s * 0x8000 : s * 0x7FFF, true)
  }
  return buffer
}
