/** 제출 실패 후 첫 오류 입력으로 포커스를 옮긴다. 상태 반영(렌더) 뒤에 실행되도록 한 프레임 미룬다. */
export function focusFirstInvalid(form: HTMLFormElement | null): void {
  if (!form) return
  requestAnimationFrame(() => {
    form.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()
  })
}
