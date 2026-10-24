import { useState } from 'react'
import { useAuth } from '../auth/context'
import { Button } from '../components/Button'
import { PageHeader } from '../components/PageHeader'
import { ThemePicker } from '../components/ThemePicker'
import { useI18n } from '../i18n/context'
import { LanguagePicker } from '../i18n/LanguagePicker'

/** 화면 설정(테마·언어)과 계정(로그인한 운영자, 로그아웃). 사이드바의 톱니바퀴로 연다. */
export function SettingsPage() {
  const { operator, signOut } = useAuth()
  const { t } = useI18n()
  const [busy, setBusy] = useState(false)

  return (
    <>
      <PageHeader title={t('settings.title')} />
      <div className="settings">
        <section className="settings-section" aria-labelledby="settings-display">
          <h2 id="settings-display">{t('settings.display')}</h2>
          <p className="muted small">{t('settings.displayNote')}</p>
          <div className="settings-fields">
            <ThemePicker />
            <LanguagePicker />
          </div>
        </section>
        <section className="settings-section" aria-labelledby="settings-account">
          <h2 id="settings-account">{t('settings.account')}</h2>
          <dl className="settings-dl">
            <dt>{t('settings.signedInAs')}</dt>
            <dd translate="no">{operator?.email}</dd>
          </dl>
          <div>
            <Button
              busy={busy}
              onClick={async () => {
                setBusy(true)
                try {
                  await signOut()
                } catch {
                  // 서버 로그아웃이 실패해도 signOut이 메모리 세션을 지웠다. 화면은 로그인으로 넘어간다.
                } finally {
                  setBusy(false)
                }
              }}
            >
              {t('layout.signOut')}
            </Button>
          </div>
        </section>
      </div>
    </>
  )
}
