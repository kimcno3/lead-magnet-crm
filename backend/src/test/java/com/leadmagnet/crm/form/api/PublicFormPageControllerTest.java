package com.leadmagnet.crm.form.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.form.application.PublicFormService;
import com.leadmagnet.crm.form.domain.Form;
import com.leadmagnet.crm.tracking.api.VisitorIdCookie;
import com.leadmagnet.crm.tracking.application.VisitRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** 페이지 컨트롤러는 404·410만 HTML 안내로 바꾸고, 나머지 오류는 전역 ProblemDetail 처리로 넘긴다. */
class PublicFormPageControllerTest {

    PublicFormService service = mock(PublicFormService.class);
    VisitRecorder visits = mock(VisitRecorder.class);
    VisitorIdCookie cookie = mock(VisitorIdCookie.class);
    PublicFormPageController controller = new PublicFormPageController(service, visits, cookie);

    @Test
    void 없는_폼과_마감된_폼은_HTML_안내다() {
        when(service.getOpen("gone-form")).thenThrow(new DomainException(ErrorCode.GONE));
        when(service.getOpen("missing")).thenThrow(new DomainException(ErrorCode.NOT_FOUND));

        ResponseEntity<String> gone = controller.page("gone-form", null, get(), new MockHttpServletResponse());
        ResponseEntity<String> missing = controller.page("missing", null, get(), new MockHttpServletResponse());

        assertThat(gone.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(gone.getBody()).contains("마감된 신청입니다");
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).contains("신청 폼을 찾을 수 없습니다");
        verifyNoInteractions(visits, cookie);
    }

    @Test
    void 그_밖의_도메인_오류는_그대로_던져_전역_처리에_맡긴다() {
        DomainException conflict = new DomainException(ErrorCode.INVALID_FORM_TRANSITION);
        when(service.getOpen("spring")).thenThrow(conflict);

        assertThatThrownBy(() -> controller.page("spring", null, get(), new MockHttpServletResponse()))
            .isSameAs(conflict);
    }

    @Test
    void HEAD는_방문을_기록하지_않는다() {
        when(service.getOpen("spring")).thenReturn(mock(Form.class));
        MockHttpServletRequest head = new MockHttpServletRequest("HEAD", "/f/spring");

        controller.page("spring", null, head, new MockHttpServletResponse());

        verify(cookie).resolve(any(), any());
        verifyNoInteractions(visits);
    }

    private static MockHttpServletRequest get() {
        return new MockHttpServletRequest("GET", "/f/x");
    }
}
