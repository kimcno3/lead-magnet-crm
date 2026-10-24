import { Link } from 'react-router'
import { PageHeader } from '../components/PageHeader'
import { useI18n } from '../i18n/context'

export function NotFoundPage() {
  const { t, rich } = useI18n()
  return (
    <>
      <PageHeader title={t('notFound.title')} />
      <p>{rich('notFound.body', { link: <Link key="link" to="/campaigns">{t('notFound.link')}</Link> })}</p>
    </>
  )
}
