import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { createCampaign, listCampaigns } from '../api/campaigns'
import { campaignListStats } from '../api/stats'
import { StatusBadge } from '../components/Badge'
import { Button } from '../components/Button'
import { Dialog } from '../components/Dialog'
import { EmptyState } from '../components/EmptyState'
import { ErrorMessage } from '../components/ErrorMessage'
import { errorText } from '../errors'
import { Field } from '../components/Field'
import { PageHeader } from '../components/PageHeader'
import { SkeletonRows, TableWrap } from '../components/Table'
import { formatNumber, formatPercent, formatPeriod, seoulDateToInstant } from '../format'
import { useAsync, useFocusFirstInvalid } from '../hooks'
import { RETENTION_DAYS } from '../labels'
import { RetentionDaysField } from '../components/RetentionDaysField'

/** 지표는 보조 정보다. 지표 조회가 실패해도 목록은 보여주고 오류만 따로 돌려준다. */
async function loadRows() {
  const [campaigns, stats] = await Promise.all([
    listCampaigns(),
    campaignListStats().then(
      (value) => ({ value, error: undefined as unknown }),
      (error: unknown) => ({ value: undefined, error }),
    ),
  ])
  const byId = new Map(stats.value?.campaigns.map((s) => [s.campaignId, s]) ?? [])
  return { rows: campaigns.map((c) => ({ ...c, metrics: byId.get(c.id) })), statsError: stats.error }
}

export function CampaignListPage() {
  const navigate = useNavigate()
  const { data: result, error, loading, reload } = useAsync(loadRows, [])
  const data = result?.rows
  const [creating, setCreating] = useState(false)

  return (
    <>
      <PageHeader
        title="캠페인"
        actions={
          <Button variant="primary" onClick={() => setCreating(true)}>
            새 캠페인
          </Button>
        }
      />
      <ErrorMessage error={error} onRetry={reload} />
      {result?.statsError !== undefined && (
        <div className="error-box" role="alert">
          <p>지표를 불러오지 못했습니다. 목록은 지표 없이 표시합니다. ({errorText(result.statsError)})</p>
          <Button size="sm" onClick={reload}>
            다시 시도
          </Button>
        </div>
      )}
      {data && data.length === 0 ? (
        <EmptyState
          message="아직 캠페인이 없습니다. 캠페인을 만든 뒤 템플릿으로 폼을 만들고 채널별 링크를 배포하세요."
          action={
            <Button variant="primary" onClick={() => setCreating(true)}>
              첫 캠페인 만들기
            </Button>
          }
        />
      ) : (
        !error && (
          <TableWrap label="캠페인 목록">
            <table className="table table-clickable">
              <caption className="visually-hidden">캠페인 목록과 전체 기간 지표</caption>
              <thead>
                <tr>
                  <th scope="col">이름</th>
                  <th scope="col">상태</th>
                  <th scope="col">기간</th>
                  <th scope="col" className="num">방문</th>
                  <th scope="col" className="num">방문자</th>
                  <th scope="col" className="num">신청</th>
                  <th scope="col" className="num">전환율</th>
                </tr>
              </thead>
              <tbody aria-busy={loading}>
                {!data && loading ? (
                  <SkeletonRows columns={7} />
                ) : (
                  data?.map((c) => (
                    // 행 전체 클릭은 마우스 편의. 키보드·스크린리더는 이름 링크를 쓴다.
                    <tr key={c.id} onClick={() => navigate(`/campaigns/${c.id}`)}>
                      <td>
                        <Link to={`/campaigns/${c.id}`} className="row-link" onClick={(e) => e.stopPropagation()}>
                          {c.name}
                        </Link>
                      </td>
                      <td>
                        <StatusBadge status={c.status} />
                      </td>
                      <td className="num nowrap">{formatPeriod(c.startsAt, c.endsAt)}</td>
                      <td className="num">{c.metrics ? formatNumber(c.metrics.visits) : '-'}</td>
                      <td className="num">{c.metrics ? formatNumber(c.metrics.visitors) : '-'}</td>
                      <td className="num">{c.metrics ? formatNumber(c.metrics.leads) : '-'}</td>
                      <td className="num">{formatPercent(c.metrics?.conversionRate)}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </TableWrap>
        )
      )}
      <NewCampaignDialog
        open={creating}
        onClose={() => setCreating(false)}
        onCreated={(id) => {
          setCreating(false)
          navigate(`/campaigns/${id}?tab=forms`)
        }}
      />
    </>
  )
}

function NewCampaignDialog({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: (id: number) => void }) {
  const { formRef, focusFirstInvalid } = useFocusFirstInvalid()
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [retentionDays, setRetentionDays] = useState(String(RETENTION_DAYS.default))
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  function reset() {
    setName('')
    setDescription('')
    setStartDate('')
    setEndDate('')
    setRetentionDays(String(RETENTION_DAYS.default))
    setErrors({})
    setFormError(null)
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setErrors({})
    setFormError(null)
    try {
      const created = await createCampaign({
        name: name.trim(),
        description: description.trim() || null,
        startsAt: seoulDateToInstant(startDate, 'start'),
        endsAt: seoulDateToInstant(endDate, 'end'),
        retentionDays: Number(retentionDays),
      })
      reset()
      onCreated(created.id)
    } catch (err) {
      if (err instanceof ApiError && err.errors.length) {
        setErrors(err.fieldErrors)
        focusFirstInvalid()
      } else setFormError(errorText(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog
      open={open}
      title="새 캠페인"
      onClose={() => {
        reset()
        onClose()
      }}
      footer={
        <>
          <Button
            onClick={() => {
              reset()
              onClose()
            }}
          >
            취소
          </Button>
          <Button variant="primary" type="submit" form="new-campaign" busy={busy}>
            만들기
          </Button>
        </>
      }
    >
      <form ref={formRef} id="new-campaign" onSubmit={submit} noValidate>
        <Field label="이름" error={errors.name}>
          {(p) => <input {...p} type="text" value={name} autoComplete="off" onChange={(e) => setName(e.target.value)} required />}
        </Field>
        <Field label="설명" optional error={errors.description}>
          {(p) => <textarea {...p} rows={3} autoComplete="off" value={description} onChange={(e) => setDescription(e.target.value)} />}
        </Field>
        <div className="field-row">
          <Field label="시작일" optional error={errors.startsAt}>
            {(p) => <input {...p} type="date" value={startDate} max={endDate || undefined} onChange={(e) => setStartDate(e.target.value)} />}
          </Field>
          <Field label="종료일" optional error={errors.endsAt}>
            {(p) => <input {...p} type="date" value={endDate} min={startDate || undefined} onChange={(e) => setEndDate(e.target.value)} />}
          </Field>
        </div>
        <p className="muted small">날짜는 서울 시각 기준이며 종료일 하루 끝까지 포함합니다.</p>
        <RetentionDaysField value={retentionDays} onChange={setRetentionDays} error={errors.retentionDays} />
        {formError && (
          <p className="form-error" role="alert">
            {formError}
          </p>
        )}
      </form>
    </Dialog>
  )
}
