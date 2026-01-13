# 批量创建剩余的类文件
$baseDir = "D:\WorkSpace\zyp\AIOperation\spring-boot-diagnosis-system\src\main\java\com\company\diagnosis"

# 创建目录
$directories = @(
    "tool\http",
    "tool\es",
    "tool\parser",
    "adapter",
    "validator",
    "exception",
    "model\dto",
    "model\entity"
)

foreach ($dir in $directories) {
    $fullPath = Join-Path $baseDir $dir
    if (!(Test-Path $fullPath)) {
        New-Item -Path $fullPath -ItemType Directory -Force
        Write-Host "Created directory: $fullPath"
    }
}

# 创建Exception类
@"
package com.company.diagnosis.exception;

public class DiagnosisException extends RuntimeException {
    public DiagnosisException(String message) {
        super(message);
    }
    
    public DiagnosisException(String message, Throwable cause) {
        super(message, cause);
    }
}
"@ | Out-File -FilePath "$baseDir\exception\DiagnosisException.java" -Encoding UTF8

@"
package com.company.diagnosis.exception;

public class AgentException extends RuntimeException {
    public AgentException(String message) {
        super(message);
    }
    
    public AgentException(String message, Throwable cause) {
        super(message, cause);
    }
}
"@ | Out-File -FilePath "$baseDir\exception\AgentException.java" -Encoding UTF8

@"
package com.company.diagnosis.exception;

public class KnowledgeException extends RuntimeException {
    public KnowledgeException(String message) {
        super(message);
    }
    
    public KnowledgeException(String message, Throwable cause) {
        super(message, cause);
    }
}
"@ | Out-File -FilePath "$baseDir\exception\KnowledgeException.java" -Encoding UTF8

# 创建Entity类
@"
package com.company.diagnosis.model.entity;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class Session {
    private String sessionId;
    private String requestId;
    private String status;
    private Map<String, Object> context;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
"@ | Out-File -FilePath "$baseDir\model\entity\Session.java" -Encoding UTF8

# 创建DTO类
@"
package com.company.diagnosis.model.dto;

import lombok.Data;
import java.util.Map;

@Data
public class KnowledgeDocument {
    private String docId;
    private String title;
    private String content;
    private String type;
    private Map<String, Object> metadata;
    private float[] embedding;
}
"@ | Out-File -FilePath "$baseDir\model\dto\KnowledgeDocument.java" -Encoding UTF8

Write-Host "All files created successfully!"
# 批量创建剩余的类文件
$baseDir = "D:\WorkSpace\zyp\AIOperation\spring-boot-diagnosis-system\src\main\java\com\company\diagnosis"

# 创建目录
$directories = @(
    "tool\http",
    "tool\es",
    "tool\parser",
    "adapter",
    "validator",
    "exception",
    "model\dto",
    "model\entity"
)

foreach ($dir in $directories) {
    $fullPath = Join-Path $baseDir $dir
    if (!(Test-Path $fullPath)) {
        New-Item -Path $fullPath -ItemType Directory -Force
        Write-Host "Created directory: $fullPath"
    }
}

# 创建Exception类
@"
package com.company.diagnosis.exception;

public class DiagnosisException extends RuntimeException {
    public DiagnosisException(String message) {
        super(message);
    }
    
    public DiagnosisException(String message, Throwable cause) {
        super(message, cause);
    }
}
"@ | Out-File -FilePath "$baseDir\exception\DiagnosisException.java" -Encoding UTF8

@"
package com.company.diagnosis.exception;

public class AgentException extends RuntimeException {
    public AgentException(String message) {
        super(message);
    }
    
    public AgentException(String message, Throwable cause) {
        super(message, cause);
    }
}
"@ | Out-File -FilePath "$baseDir\exception\AgentException.java" -Encoding UTF8

@"
package com.company.diagnosis.exception;

public class KnowledgeException extends RuntimeException {
    public KnowledgeException(String message) {
        super(message);
    }
    
    public KnowledgeException(String message, Throwable cause) {
        super(message, cause);
    }
}
"@ | Out-File -FilePath "$baseDir\exception\KnowledgeException.java" -Encoding UTF8

# 创建Entity类
@"
package com.company.diagnosis.model.entity;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class Session {
    private String sessionId;
    private String requestId;
    private String status;
    private Map<String, Object> context;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
"@ | Out-File -FilePath "$baseDir\model\entity\Session.java" -Encoding UTF8

# 创建DTO类
@"
package com.company.diagnosis.model.dto;

import lombok.Data;
import java.util.Map;

@Data
public class KnowledgeDocument {
    private String docId;
    private String title;
    private String content;
    private String type;
    private Map<String, Object> metadata;
    private float[] embedding;
}
"@ | Out-File -FilePath "$baseDir\model\dto\KnowledgeDocument.java" -Encoding UTF8

Write-Host "All files created successfully!"
