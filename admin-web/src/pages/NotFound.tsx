import { Link } from 'react-router'
import { PageHeader } from '../components/PageHeader'

export function NotFoundPage() {
  return (
    <>
      <PageHeader title="페이지를 찾을 수 없습니다" />
      <p>
        주소를 확인하거나 <Link to="/campaigns">캠페인 목록</Link>으로 돌아가세요.
      </p>
    </>
  )
}
