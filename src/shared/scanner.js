let controls,
  generation = 0;
export function stopScanner() {
  generation++;
  controls?.stop();
  controls = null;
  const v = document.querySelector('#camera');
  if (v) {
    v.srcObject?.getTracks().forEach((track) => track.stop());
    v.srcObject = null;
    v.hidden = true;
  }
}
export async function scanCamera(onResult) {
  stopScanner();
  const current = generation;
  const { BrowserQRCodeReader } = await import('@zxing/browser');
  if (current !== generation) return;
  const video = document.querySelector('#camera');
  if (!video) return;
  video.hidden = false;
  const reader = new BrowserQRCodeReader();
  const active = await reader.decodeFromConstraints(
    { audio: false, video: { facingMode: 'environment' } },
    video,
    (result, _error, control) => {
      if (result && current === generation) {
        control.stop();
        stopScanner();
        onResult(result.getText());
      }
    },
  );
  if (current !== generation) active.stop();
  else controls = active;
}
export async function scanFile(file) {
  if (!file || file.size > 5 * 1024 * 1024) throw new Error('IMAGE');
  const { BrowserQRCodeReader } = await import('@zxing/browser');
  const url = URL.createObjectURL(file);
  try {
    return (await new BrowserQRCodeReader().decodeFromImageUrl(url)).getText();
  } finally {
    URL.revokeObjectURL(url);
  }
}
