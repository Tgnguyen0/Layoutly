// Fetch private assets in the editor and give the isolated preview only their bytes.
export async function assetDataUrl(url, signal) {
  const response = await fetch(url, { signal, credentials: 'same-origin', cache: 'no-store' })
  if (!response.ok) throw new Error('Asset HTTP ' + response.status)
  const blob = await response.blob()
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = () => reject(new Error('Không thể đọc tài nguyên preview.'))
    reader.readAsDataURL(blob)
  })
}
