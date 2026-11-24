import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AIChatPanel from '../../../src/components/workspace/AIChatPanel.jsx'
import { snapshotApi } from '../../../src/lib/api.js'

vi.mock('../../../src/lib/api.js', () => ({
  snapshotApi: {
    aiStatus: vi.fn(),
    aiChat: vi.fn(),
    aiRevisions: vi.fn(),
    aiAccept: vi.fn(),
    aiReject: vi.fn()
  },
  downloadAiExport: vi.fn()
}))

describe('AIChatPanel component', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    snapshotApi.aiStatus.mockResolvedValue({ json: { available: true, provider: 'OpenAI' } })
    snapshotApi.aiRevisions.mockResolvedValue({ json: [] })
  })

  it('renders connected AI status when API is available', async () => {
    render(<AIChatPanel snapshot={{ snapshotId: 'snap123' }} onClose={() => {}} />)

    await waitFor(() => {
      expect(screen.getByText(/Connected \(OpenAI\)/i)).toBeInTheDocument()
    })
    expect(screen.getByPlaceholderText(/Nhap yeu cau chinh sua code.../i)).not.toBeDisabled()
  })

  it('renders disconnected message when AI status is unavailable', async () => {
    snapshotApi.aiStatus.mockResolvedValue({ json: { available: false, message: 'Missing API key' } })
    render(<AIChatPanel snapshot={{ snapshotId: 'snap123' }} onClose={() => {}} />)

    await waitFor(() => {
      expect(screen.getByText(/Chua ket noi/i)).toBeInTheDocument()
    })
    expect(screen.getByPlaceholderText(/Dich vu AI chưa duoc ket noi.../i)).toBeDisabled()
  })

  it('sends user prompt and displays AI draft revision response', async () => {
    snapshotApi.aiChat.mockResolvedValue({
      json: {
        message: 'Da doi mau nut thanh vang',
        revisionId: 'rev_001',
        status: 'DRAFT',
        modifiedFiles: ['src/App.jsx'],
        diffs: [{ filePath: 'src/App.jsx', changeType: 'MODIFIED', patchUnified: '--- a/src/App.jsx\n+++ b/src/App.jsx' }],
        buildSuccess: true
      }
    })

    render(<AIChatPanel snapshot={{ snapshotId: 'snap123' }} onClose={() => {}} />)

    await waitFor(() => expect(screen.getByPlaceholderText(/Nhap yeu cau/i)).not.toBeDisabled())

    const input = screen.getByPlaceholderText(/Nhap yeu cau/i)
    fireEvent.change(input, { target: { value: 'Doi mau nut thanh vang' } })
    fireEvent.click(screen.getByTitle(/Gui yeu cau AI/i))

    await waitFor(() => {
      expect(screen.getByText(/Da doi mau nut thanh vang/i)).toBeInTheDocument()
      expect(screen.getByText(/Draft Revision Propose: rev_001/i)).toBeInTheDocument()
    })
  })

  it('handles accept revision button click', async () => {
    snapshotApi.aiChat.mockResolvedValue({
      json: {
        message: 'Draft created',
        revisionId: 'rev_002',
        status: 'DRAFT',
        modifiedFiles: ['src/App.jsx']
      }
    })
    snapshotApi.aiAccept.mockResolvedValue({ json: { status: 'ACCEPTED' } })
    const onAccepted = vi.fn()

    render(<AIChatPanel snapshot={{ snapshotId: 'snap123' }} onClose={() => {}} onRevisionAccepted={onAccepted} />)

    await waitFor(() => expect(screen.getByPlaceholderText(/Nhap yeu cau/i)).not.toBeDisabled())

    const input = screen.getByPlaceholderText(/Nhap yeu cau/i)
    fireEvent.change(input, { target: { value: 'Fix styles' } })
    fireEvent.click(screen.getByTitle(/Gui yeu cau AI/i))

    await waitFor(() => expect(screen.getByText(/Chap nhan/i)).toBeInTheDocument())

    fireEvent.click(screen.getByText(/Chap nhan/i))

    await waitFor(() => {
      expect(snapshotApi.aiAccept).toHaveBeenCalledWith('snap123', 'rev_002', 'REACT')
      expect(onAccepted).toHaveBeenCalled()
    })
  })
})
