package backend

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"time"
)

// Client는 worker가 사용하는 backend judge job API 호출을 담당합니다.
type Client struct {
	baseURL    string
	httpClient *http.Client
}

// NewClient는 backend API client를 생성하고 baseURL 끝의 슬래시를 정리합니다.
func NewClient(baseURL string) *Client {
	return &Client{
		baseURL: strings.TrimRight(baseURL, "/"),
		httpClient: &http.Client{
			Timeout: 10 * time.Second,
		},
	}
}

// NextJob은 backend에서 다음 대기 중인 채점 작업을 하나 가져옵니다.
// 가져올 작업이 없으면 nil을 반환합니다.
func (c *Client) NextJob(ctx context.Context) (*JudgeJobAssignment, error) {
	req, err := http.NewRequestWithContext(
		ctx,
		http.MethodPost,
		c.baseURL+"/api/judge-jobs/next",
		nil,
	)
	if err != nil {
		return nil, err
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusNoContent {
		return nil, nil
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("next judge job failed: status=%d", resp.StatusCode)
	}

	var job JudgeJobAssignment
	if err := json.NewDecoder(resp.Body).Decode(&job); err != nil {
		return nil, err
	}

	return &job, nil
}

// CompleteJob은 실행 중인 채점 작업의 최종 verdict를 backend에 보고합니다.
func (c *Client) CompleteJob(ctx context.Context, jobID int64, verdict string) error {
	body, err := json.Marshal(CompleteRequest{
		Verdict: verdict,
	})
	if err != nil {
		return err
	}

	req, err := http.NewRequestWithContext(
		ctx,
		http.MethodPost,
		fmt.Sprintf("%s/api/judge-jobs/%d/complete", c.baseURL, jobID),
		bytes.NewReader(body),
	)
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("complete judge job failed: status=%d", resp.StatusCode)
	}

	return nil
}
