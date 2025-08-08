# Github Repository와 Jira Project 연결 + Ktlint Check 적용

## Github Action Workflow 작업

1. 깃허브 이슈 생성 시 Jira와 연동
2. Workflow에 Pull Request 발생 시 Ktlint 적용해서 Code Convention이 잘 맞는지 확인하는 작업

   => ktlint는 ktlint에 따라 코드 작성을 하지 않을 시 Pull Request에서 workflow fail이 발생

## Github와 Jira 연동

1. Jira에서 프로젝트 생성
2. (Jira) Side 바 → 앱 → 더 많은 앱 살펴보기 → **Github For Atlassian 앱 연동**
3. (Github)  Settings → Applications → Jira → Configure → **Repository access**에서 연결 할 Repository 추가 → Save
4. 적용하고자 하는 Github Repository에 yaml 안에 들어갈 `secrets` 값, 토큰 발급 등 작업 수행
    - Github Token 발급
        - `secrets.GITHUB_TOKEN` 을 기본으로 제공하지만 별도로 Token 만들고 secrets에 넣어 활용 가능
    - Jira API Token 발급
        - Jira의 계정 설정 → 보안 → API 토큰 항목의 토큰 만들기 및 관리에서 제작 (1년까지)
        - Repository Settings → secrets and variables (좌측 사이드 바) → Actions → Repository Secrets에 `JIRA_API_TOKEN`, `JIRA_USER_EMAIL`, `JIRA_BASE_URL` 값 추가

</br>

> Github Issue 제작 Form (일반적인 이슈 제작보다 구체적인 형태 제공)
(.github/template/issue_form.yaml)
> 
```yaml
name: '이슈 생성'
description: 'Jira와 연동되는 ISSUE'
labels: [order]
title: '이슈 제목 작성'
body:
  - type: input
    id: parentKey
    attributes:
      label: '상위 작업 Ticket Number'
      description: '상위 작업의 Ticket Number 기입'
      placeholder: 'ABC-X' # 앞에 접두어로 Jira Project Key 값 입력
    validations: # 필수 사항인지 아닌지 설정
      required: true

  - type: input
    id: description
    attributes:
      label: '이슈 내용(Description)'
      description: '이슈에 대한 간단한 설명'
      value: |
        - Ex) 깃허브 저장소와 Jira 프로젝트 연결
    validations:
      required: true

  - type: textarea
    id: details
    attributes:
      label: '상세 내용(Details)'
      description: '이슈에 대한 자세한 설명 및 논의 할 내용'
      value: |
        - Ex) Atlassian 사에서 만든 Github for Atlassian 앱 이용
        - ~~~
    validations:
      required: true

  - type: textarea
    id: tasks
    attributes:
      label: '체크리스트(Tasks)'
      description: '해당 이슈에 대해 필요한 작업목록을 작성해주세요'
      value: |
        - [ ] ex)
        - [ ] 작업1
        - [ ] 작업2
    validations:
      required: true

  - type: textarea
    id: references
    attributes:
      label: '참조(References)'
      description: '이슈와 관련된 래퍼런스'
      value: |
        - Ex) 블로그 링크 참조, 공식문서 참조
    validations:
      required: false
```

</br>

> Github Issue 생성 시 Jira Issue를 생성하는 Workflow
(.github/workflows/issue-connect.yaml)
> 

```yaml
name: Create Jira Issue
# 이슈 최초 생성 시만 Trigger 되도록 설정
on:
  issues:
    types: [opened]

permissions:
  contents: write
  issues: write

# 수행 될 Job
jobs:
  create-issue:
    name: Create Jira Issue
    runs-on: ubuntu-latest
    steps:
      # Jira에 로그인
      - name: Login
        uses: atlassian/gajira-login@v3
        env:
          JIRA_BASE_URL: ${{ secrets.JIRA_BASE_URL }}
          JIRA_API_TOKEN: ${{ secrets.JIRA_API_TOKEN }}
          JIRA_USER_EMAIL: ${{ secrets.JIRA_USER_EMAIL }}

      # Main Branch로 부터 코드 참조
      - name: Checkout main code
        uses: actions/checkout@v4
        with:
          ref: main

      # 이슈 파싱 (Issue Template으로 부터 파싱해오기 -> issue.body 내용 파싱)
      - name: Issue Parser
        uses: stefanbuck/github-issue-parser@v3
        id: issue-parser
        with:
          template-path: .github/ISSUE_TEMPLATE/issue_form.yaml

      # 이슈 파싱을 잘 했는지 확인하는 로그 출력
      - name: Log Issue Parser
        run: |
          echo '${{ steps.issue-parser.outputs.jsonString }}'

      # 마크다운 이슈 형태를 Jira에 맞게 전환
      - name: Convert markdown to Jira Syntax
        uses: peter-evans/jira2md@v2
        id: md2jira
        with:
          input-text: |
            ### Github Issue Link
            - ${{ github.event.issues.html_url }}
            
            ${{ github.event.issue.body }}
          mode: md2jira

      # Jira 이슈 생성
      - name: Create Issue
        id: create
        uses: atlassian/gajira-create@v3
        with:
          # 연동 할 Jira Project Key 값 입력
          project: ABC
          # 이슈 타입 선택 (이 부분에 들어갈 값이 Custom 등 하는 것에 따라 달라지는 것 같다)
          issuetype: Task
          summary: "${{ github.event.issue.title }}"
          description: "${{ steps.md2jira.outputs.output-text }}"
          fields: |
            {
              "parent": {
                "key": "${{ steps.issue-parser.outputs.issueparser_parentKey }}"
              }
            }

      # 생성된 이슈 로그 출력
      - name: Created issue's Log
        run: echo "Jira Issue ${{ steps.create.outputs.issue }} was created under parent ${{ steps.issue-parser.outputs.issueparser_parentKey }}"

      # Issue에 입력한 Ticket Number 기반으로 Branch 생성
      - name: Create branch with Ticket Number
        run: |
          ISSUE_NUMBER="${{ steps.create.outputs.issue }}"
          ISSUE_TITLE="${{ steps.issue-parser.outputs.issueparser_description }}"
          BRANCH_NAME="${ISSUE_NUMBER}-$(echo ${ISSUE_TITLE} | sed 's/ /-/g')"
          git checkout -b "${BRANCH_NAME}"
          git push --set-upstream origin "${BRANCH_NAME}"

      # 이슈 제목 Update
      - name: Update Issue Title
        uses: actions-cool/issues-helper@v3
        with:
          actions: 'update-issue'
          token: ${{ secrets.GH_PAT }}
          title: '[${{ steps.create.outputs.issue }}] ${{ github.event.issue.title }}'

      # Jira 링크 연결하는 댓글 추가
      - name: Add Comment With Jira Issue Link
        uses: actions-cool/issues-helper@v3
        with:
          actions: 'create-comment'
          token: ${{ secrets.GH_PAT }}
          issue-number: ${{ github.event.issue.number }}
          body: "Jira Issue Created: [${{ steps.create.outputs.issue }}](${{ secrets.JIRA_BASE_URL }}/browse/${{ steps.create.outputs.issue }})"

```

## Ktlint 적용하기

- yaml 파일 및 gradle 설정이 필요하다
    
    > build.gradle.kts (Project 수준)
    > 
    
    ```kotlin
    plugins {
    		alias(libs.plugins.ktlint) apply false
    }
    
    subprojects {
        apply(plugin = "org.jlleitschuh.gradle.ktlint")
    
        // 선택: 세부 옵션
        configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
            version.set("1.7.1")      // ktlint CLI 엔진 버전 고정 (생략 가능)
            android.set(true)         // Android Kotlin 스타일 적용
            reporters {               // CI에서 읽기 쉬운 리포트 형식 추가
                reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
                reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
            }
            filter {
                exclude("**/generated/**")
                include("**/*.kt", "**/*.kts")
            }
        }
    }
    ```
    
    - app 수준의 gradle 파일의 plugin에도 다음 내용 추가
        
        `alias(*libs*.*plugins*.*ktlint*)`
        
    
    > version catalog
    > 
    
    ```toml
    ktlintVersion = "13.0.0"
    ktlint = {id = "org.jlleitschuh.gradle.ktlint", version.ref = "ktlintVersion"}
    ```
    
    > yaml 파일
    > 
    
    ```yaml
    name: KtLint Check
    
    # Pull Request 생성 및 Update시 Workflow가 Trigger되며
    # gradlew, wrapper, kotlin 파일이 바뀌었을 때만 실행하여 CI 비용 절감
    on:
      pull_request:
        paths:
          - '**/*.kt'
          - '**/*.kts'
          - '**/*.gradle*'
          - 'gradle/wrapper/**'
    
    # 실행 환경 설정
    jobs:
      ktlint:
        runs-on: ubuntu-latest
    
        # 권한 설정
        permissions:
          contents: read
          pull-requests: write
    
        # 코드 가져오기 (Workflow가 소스 트리를 사용할 수 있도록 checkout)
        steps:
          - uses: actions/checkout@v4
    
          # Temurin jdk 21 버전을 설치하고 캐싱을 자동화
          - uses: actions/setup-java@v4
            with:
              distribution: 'temurin'
              java-version: '21'
              cache: gradle
    
          # gradlew 스크립트가 Permission Denied 되지 않도록 실행 옵션 부여
          - name: Make gradlew executable
            run: chmod +x ./gradlew
    
          # gradle-wrapper.jar 파일을 검증
          - name: Validate Gradle wrapper
            uses: gradle/actions/wrapper-validation@v4
    
          # 과거 workflow 호환용으로 두거나 세밀한 cache 작업 시 필요
          - name: Cache Gradle packages
            uses: actions/cache@v4
            with:
              path: ~/.gradle/caches
              key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
              restore-keys: ${{ runner.os }}-gradle-
    
          # ./gradlew ktlintCheck로 Ktlint 플러그인이 모든 Source Set을 스캔
          # 규칙 위반 시 빌드 실패 -> merge 전에 모든 규칙을 만족하도록 조정
          - name: Run ktlint
            run: ./gradlew ktlintCheck
    
          # build/reports/ktlint/** 디렉토리를 아티펙트로 업로드해 결과를 볼 수 있도록 한다
          - name: Upload ktlint report
            if: failure()
            uses: actions/upload-artifact@v4
            with:
              name: ktlint-report
              path: |
                build/reports/ktlint
    ```
    
## 주의 사항 및 Trouble Shooting
<details>
  <summary>주의사항 </summary>
  Wokflow Permission 설정

- yaml 파일에서 Permissions 설정 부분에 다음과 같이 입력 되어있다

```yaml
permissions:
	contents: write
	issues: write
```

- 만약 Workflow Permissions 부분에 write 권한이 없다면 Workflow 실행 시 문제가 될 수 있다
    - GITHUB_TOKEN 값을 읽어올 수 가 없다
    - 기본적으로 대부분 2번째 Read Only 부분만 선택되어 있는 것 같다

<img width="723" height="363" alt="Image" src="https://github.com/user-attachments/assets/fc08cf2a-216b-4631-b434-56c773712352" />

## Github Token

> Github_Jira_Token (GH_PAT)
> 

```
토큰 값 잘 보관하기
```

## Jira API Token

```
토큰 값 잘 보관하기
```

> Env에 넣을 상수 값들
> 

```
JIRA_BASE_URL = "https://~~.atlassian.net"
JIRA_API_TOKEN = Jira API Token 값 참조
JIRA_USER_EMAIL = "Jira 회원가입한 이메일"
```

## Issue Template 경로

- github-issue-parser Github 저장소에 가면 경로가 `.github/ISSUE_TEMPLATE` 로 하라고 가이드가 나와있다

> Ex)
> 

```
- uses: stefanbuck/github-issue-parser@v3
  id: issue-parser
  with:
    template-path: .github/ISSUE_TEMPLATE/bug-report.yml
```

## Branch 명에 이슈 제목 연결 안되는 문제

- 작성한 issue form에선 생성하는 이슈의 제목을 `description` 이라고 되어있었는데 단순히 yaml 파일을 다른 사람이 만든 것을 보고 `branch` 로 값을 가져와 불러올 수가 없었음

## Issue Comment에 Jira Link 안 걸리는 문제

- 설정 파일에 오타가 존재했음 `.` 이 필요한데 `-` 로 입력되어 읽어오지 못했음
</details>

<details>
  <summary>Trouble Shooting</summary>
1. Issue type 관련 문제
    - ####이 있는 부분에 보면 body의 `issuetype` 값이 빈 값으로 들어있는 것을 볼 수 있다 여기서 이슈타입을 지정하지 못해 발생하는 에러 같다

```
Warning: Unexpected input(s) 'issue type', valid inputs are ['project', 'issuetype', 'summary', 'description', 'fields']
Run atlassian/gajira-create@v3
Error: Jira API error
    at Jira.fetch (/home/runner/work/_actions/atlassian/gajira-create/v3/webpack:/create/common/net/Jira.js:108:1)
    at processTicksAndRejections (node:internal/process/task_queues:95:5)
    at module.exports.execute (/home/runner/work/_actions/atlassian/gajira-create/v3/webpack:/create/action.js:76:1)
    at exec (/home/runner/work/_actions/atlassian/gajira-create/v3/dist/index.js:32538:20) {
  req: {
    method: 'POST',
    ####################
    body: '{"fields":{"project":{"key":"ABC"},"issuetype":{"name":""},"summary":"Issue Comment 제대로 남는지 확인","description":"h3. Github Issue Link\\n- \\n\\nh3. 상위 작업 Ticket Number\\n\\nABC-3\\n\\nh3. 이슈 내용(Description)\\n\\nWorkflow 테스트\\n\\nh3. 상세 내용(Details)\\n\\n- Ex) Atlassian 사에서 만든 Github for Atlassian 앱 이용\\n- ~~~\\n\\n\\nh3. 체크리스트(Tasks)\\n\\n- [ ] ex)\\n- [ ] 작업1\\n- [ ] 작업2\\n\\n\\nh3. 참조(References)\\n\\n- Ex) 블로그 링크 참조, 공식문서 참조","parent":{"key":"ABC-3"}}}',
    url: '***/rest/api/2/issue'
  },
  res: {
    headers: [Object: null prototype] {
      'content-type': [Array],
      'transfer-encoding': [Array],
      connection: [Array],
      date: [Array],
      server: [Array],
      'timing-allow-origin': [Array],
      'x-arequestid': [Array],
      'set-cookie': [Array],
      'x-aaccountid': [Array],
      'cache-control': [Array],
      'x-content-type-options': [Array],
      'x-xss-protection': [Array],
      'atl-traceid': [Array],
      'atl-request-id': [Array],
      'strict-transport-security': [Array],
      'report-to': [Array],
      nel: [Array],
      'server-timing': [Array],
      'x-cache': [Array],
      via: [Array],
      'x-amz-cf-pop': [Array],
      'x-amz-cf-id': [Array]
    },
    status: 400,
    body: { errorMessages: [], errors: [Object] }
  },
  originError: Error: Bad Request
      at /home/runner/work/_actions/atlassian/gajira-create/v3/webpack:/create/common/net/client.js:30:1
      at processTicksAndRejections (node:internal/process/task_queues:95:5)
      at Jira.fetch (/home/runner/work/_actions/atlassian/gajira-create/v3/webpack:/create/common/net/Jira.js:98:1)
      at module.exports.execute (/home/runner/work/_actions/atlassian/gajira-create/v3/webpack:/create/action.js:76:1)
      at exec (/home/runner/work/_actions/atlassian/gajira-create/v3/dist/index.js:32538:20),
  source: 'jira',
  jiraError: { issuetype: '유효한 이슈 유형을 지정하세요' }
}
```
원인 : issuetype 부분에 초록 밑줄이 있어 IDE가 권장하는 대로 issue type이라고 했더니 읽어오지 못하고 빈 값이 들어오는 문제 발생

<img width="641" height="382" alt="Image" src="https://github.com/user-attachments/assets/5741469e-eb5a-4321-9732-7e571c8ce180" />

</br>

2. Issue Parsing 제대로 못하는 문제
- 쉘 스크립트가 문자를 명령어 구문으로 해석하려다 잘못된 위치라고 에러 출력

<img width="746" height="904" alt="Image" src="https://github.com/user-attachments/assets/5525c340-d88a-412d-8a93-3d4a3c92047d" />

</br>

3. ktlint `./gradlew ktlintCheck` 명령이 제대로 실행되지 않은 상황
    - gradlew / gradlew.bat : 쉘 또는 배치 스크립트 내부에서 `org.gradle.wrapper.GradlewWrapperMain` 클래스를 실행해 Gradle을 부팅
    - gradle/wrapper/gradle-wrapper.jar : 위의 Class가 들어있는 Jar 파일로 실제 빌드에 해당 Gradle 런타임을 전달해준다
    - **gradlew 쉘 스크립트는 JAR 없이 실행 불가능**
    
    ⇒ `gradle/wrapper/gradle-wrapper.jar` 파일이 gitignore에 포함되어 있어서 Ktlint 체크를 위한 `./gradlew ktlintCheck` 가 실행될 수 없어서 gitignore에서 제외 시켰다
    
    <img width="751" height="267" alt="Image" src="https://github.com/user-attachments/assets/0c4432cf-4836-4fd5-a5e9-d92e386e19c7" />
</details>

---
## Reference

- 공식 문서
    
    🖇️ https://github.com/stefanbuck/github-issue-parser
    
- 참고 블로그
    
    🖇️ [프로젝트에서 Github와 Jira 함께 사용하기 (3) - Github Actions로 Jira 이슈 등록 자동화 — 시니유의 개발 블로그](https://lamerry.tistory.com/entry/%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8%EC%97%90%EC%84%9C-Jira-%EC%82%AC%EC%9A%A9%ED%95%98%EA%B8%B0-3-Github-Actions%EB%A1%9C-GithubJira-%EC%9D%B4%EC%8A%88-%EC%9E%90%EB%8F%99%ED%99%94?category=1265013)
    
    🖇️ [Github 협업 설정(Github Issue + Jira 연동)](https://velog.io/@sangpok/Github-%ED%98%91%EC%97%85-%EC%84%A4%EC%A0%95Github-Issue-Jira-%EC%97%B0%EB%8F%99)
    

- ktlint, detekt, debug build  실행
    
    🖇️ [우리가 협업하는 방법 (2) Ktlint, Detekt, Github-Action CI/CD | by Yoon Woohee | Medium](https://medium.com/@DevYoon/%EC%95%88%EB%93%9C%EB%A1%9C%EC%9D%B4%EB%93%9C-%EA%B0%9C%EB%B0%9C-%EC%9D%BC%EC%A7%80-7-%EC%9A%B0%EB%A6%AC%EA%B0%80-%ED%98%91%EC%97%85%ED%95%98%EB%8A%94-%EB%B0%A9%EB%B2%95-2-ktlint-detekt-github-action-ci-cd-60d7fbb9af50)

