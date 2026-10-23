/**
 * 업로드 HTML 미리보기. 보안 불변식(CLAUDE.md, ADR 0002·0011):
 * - sandbox는 정확히 "allow-scripts allow-forms". allow-same-origin을 넣으면 업로드 HTML이
 *   관리자 출처로 실행되어 access token과 API에 닿으므로 절대 넣지 않는다.
 * - 내용은 srcdoc으로만 넣는다. dangerouslySetInnerHTML이나 src(blob:, data:)를 쓰지 않는다.
 * - srcdoc 문서는 관리자 CSP를 상속하므로 스크립트가 실행되지 않는 정적 미리보기가 된다.
 */
export const PREVIEW_SANDBOX = 'allow-scripts allow-forms'

export function SandboxPreview({ html, title }: { html: string; title: string }) {
  return <iframe className="preview-frame" title={title} sandbox={PREVIEW_SANDBOX} srcDoc={html} referrerPolicy="no-referrer" />
}
