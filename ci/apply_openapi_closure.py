from pathlib import Path

ROOT = Path('private-repo')


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')


def write(path: str, text: str) -> None:
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding='utf-8')


def replace(path: str, old: str, new: str) -> None:
    text = read(path)
    if old not in text:
        if new in text:
            return
        raise SystemExit(f'missing OpenAPI replacement marker in {path}: {old[:160]!r}')
    write(path, text.replace(old, new, 1))

write('backend-java/model/src/main/resources/admin/openapi-site-metadata.yaml', '''openapi: 3.0.3
info:
  title: Wust Dormitory Select 站点元数据接口
  version: 1.2.0
paths:
  /api/v1/public/site-config:
    get:
      tags: [PublicSiteMetadata]
      operationId: getPublicSiteConfig
      summary: 获取登录页公开站点配置
      responses:
        '200':
          description: 当前公开站点配置
          content:
            application/json:
              schema:
                $ref: '../common-response/openapi-common-response.yaml#/components/schemas/ObjectSuccessResponse'
  /api/v1/public/site-assets/{slot}:
    get:
      tags: [SiteMetadataAsset]
      operationId: getPublicSiteAsset
      summary: 读取当前生效的站点图片素材
      parameters:
        - $ref: '#/components/parameters/SiteAssetSlot'
      responses:
        '200':
          description: 当前图片二进制内容
          content:
            application/octet-stream:
              schema:
                type: string
                format: binary
        '404': { $ref: '#/components/responses/ErrorResponse' }
  /api/v1/admin/settings/login-page:
    get:
      tags: [AdminSiteMetadata]
      operationId: getAdminLoginPageSetting
      summary: 获取学校管理员可维护的登录页配置
      security:
        - bearerAuth: []
      responses:
        '200':
          description: 当前登录页配置及是否可编辑
          content:
            application/json:
              schema:
                $ref: '../common-response/openapi-common-response.yaml#/components/schemas/ObjectSuccessResponse'
        '401': { $ref: '#/components/responses/ErrorResponse' }
        '403': { $ref: '#/components/responses/ErrorResponse' }
    put:
      tags: [AdminSiteMetadata]
      operationId: updateAdminLoginPageSetting
      summary: 更新学校管理员登录页展示内容
      security:
        - bearerAuth: []
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/LoginPageContentUpdateRequest'
      responses:
        '200':
          description: 更新后的登录页配置
          content:
            application/json:
              schema:
                $ref: '../common-response/openapi-common-response.yaml#/components/schemas/ObjectSuccessResponse'
        '400': { $ref: '#/components/responses/ErrorResponse' }
        '401': { $ref: '#/components/responses/ErrorResponse' }
        '403': { $ref: '#/components/responses/ErrorResponse' }
  /api/v1/admin/settings/site-assets/{slot}:
    post:
      tags: [SiteMetadataAsset]
      operationId: uploadAdminSiteAsset
      summary: 学校管理员上传已授权的站点图片素材
      security:
        - bearerAuth: []
      parameters:
        - $ref: '#/components/parameters/SiteAssetSlot'
      requestBody:
        required: true
        content:
          multipart/form-data:
            schema:
              type: object
              required: [file]
              properties:
                file:
                  type: string
                  format: binary
      responses:
        '200':
          description: 上传后的受控图片描述
          content:
            application/json:
              schema:
                $ref: '../common-response/openapi-common-response.yaml#/components/schemas/ObjectSuccessResponse'
        '400': { $ref: '#/components/responses/ErrorResponse' }
        '401': { $ref: '#/components/responses/ErrorResponse' }
        '403': { $ref: '#/components/responses/ErrorResponse' }
  /api/v1/admin/settings/student-theme:
    get:
      tags: [AdminSiteMetadata]
      operationId: getAdminStudentThemeSetting
      summary: 获取学生端主题及学校修改权限
      security:
        - bearerAuth: []
      responses:
        '200':
          description: 当前学生端主题设置
          content:
            application/json:
              schema:
                $ref: '../common-response/openapi-common-response.yaml#/components/schemas/ObjectSuccessResponse'
        '401': { $ref: '#/components/responses/ErrorResponse' }
        '403': { $ref: '#/components/responses/ErrorResponse' }
    put:
      tags: [AdminSiteMetadata]
      operationId: updateAdminStudentThemeSetting
      summary: 学校管理员修改学生端主题
      security:
        - bearerAuth: []
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/StudentThemeUpdateRequest'
      responses:
        '200':
          description: 更新后的学生端主题设置
          content:
            application/json:
              schema:
                $ref: '../common-response/openapi-common-response.yaml#/components/schemas/ObjectSuccessResponse'
        '400': { $ref: '#/components/responses/ErrorResponse' }
        '401': { $ref: '#/components/responses/ErrorResponse' }
        '403': { $ref: '#/components/responses/ErrorResponse' }
  /api/v1/platform/site-metadata:
    get:
      tags: [PlatformSiteMetadata]
      operationId: getPlatformSiteMetadata
      summary: 获取系统管理员站点元数据配置
      security:
        - bearerAuth: []
      responses:
        '200':
          description: 当前站点元数据配置
          content:
            application/json:
              schema:
                $ref: '../common-response/openapi-common-response.yaml#/components/schemas/ObjectSuccessResponse'
        '401': { $ref: '#/components/responses/ErrorResponse' }
        '403': { $ref: '#/components/responses/ErrorResponse' }
    put:
      tags: [PlatformSiteMetadata]
      operationId: updatePlatformSiteMetadata
      summary: 更新系统管理员站点元数据配置
      security:
        - bearerAuth: []
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/PlatformSiteMetadataUpdateRequest'
      responses:
        '200':
          description: 更新后的站点元数据配置
          content:
            application/json:
              schema:
                $ref: '../common-response/openapi-common-response.yaml#/components/schemas/ObjectSuccessResponse'
        '400': { $ref: '#/components/responses/ErrorResponse' }
        '401': { $ref: '#/components/responses/ErrorResponse' }
        '403': { $ref: '#/components/responses/ErrorResponse' }
  /api/v1/platform/site-metadata/assets/{slot}:
    post:
      tags: [SiteMetadataAsset]
      operationId: uploadPlatformSiteAsset
      summary: 系统管理员上传站点图片素材
      security:
        - bearerAuth: []
      parameters:
        - $ref: '#/components/parameters/SiteAssetSlot'
      requestBody:
        required: true
        content:
          multipart/form-data:
            schema:
              type: object
              required: [file]
              properties:
                file:
                  type: string
                  format: binary
      responses:
        '200':
          description: 上传后的受控图片描述
          content:
            application/json:
              schema:
                $ref: '../common-response/openapi-common-response.yaml#/components/schemas/ObjectSuccessResponse'
        '400': { $ref: '#/components/responses/ErrorResponse' }
        '401': { $ref: '#/components/responses/ErrorResponse' }
        '403': { $ref: '#/components/responses/ErrorResponse' }
components:
  parameters:
    SiteAssetSlot:
      name: slot
      in: path
      required: true
      description: 站点图片槽位（SQUARE_LOGO、HORIZONTAL_LOGO 或 LOGIN_IMAGE）
      schema:
        type: string
        minLength: 1
        maxLength: 32
  responses:
    ErrorResponse:
      description: 业务错误
      content:
        application/json:
          schema:
            $ref: '../common-response/openapi-common-response.yaml#/components/schemas/ErrorResponse'
  schemas:
    LoginPageContentUpdateRequest:
      type: object
      required: [html]
      properties:
        html:
          type: string
          minLength: 1
          maxLength: 8000
        imageUrl:
          type: string
          maxLength: 500
          deprecated: true
          description: 兼容旧客户端保留；服务端忽略该字段，图片必须通过受控上传接口更新。
    StudentThemeUpdateRequest:
      type: object
      required: [studentTheme]
      properties:
        studentTheme:
          type: string
          minLength: 1
          maxLength: 16
    StudentThemeSettingUpdateRequest:
      type: object
      required: [studentTheme, schoolAdminEditable]
      properties:
        studentTheme:
          type: string
          minLength: 1
          maxLength: 16
        schoolAdminEditable:
          type: boolean
    SiteBrandingUpdateRequest:
      type: object
      required: [schoolName]
      properties:
        schoolName:
          type: string
          minLength: 1
          maxLength: 128
        squareLogoUrl:
          type: string
          maxLength: 500
          deprecated: true
          description: 兼容旧客户端保留；服务端忽略该字段，校徽必须通过受控上传接口更新。
        horizontalLogoUrl:
          type: string
          maxLength: 500
          deprecated: true
          description: 兼容旧客户端保留；服务端忽略该字段，校徽必须通过受控上传接口更新。
    AdminHomeContentUpdateRequest:
      type: object
      required: [title]
      properties:
        title:
          type: string
          minLength: 1
          maxLength: 80
        subtitle:
          type: string
          maxLength: 240
    PlatformSiteMetadataUpdateRequest:
      type: object
      required: [branding, login, schoolAdminEditable, theme]
      properties:
        branding:
          $ref: '#/components/schemas/SiteBrandingUpdateRequest'
        login:
          $ref: '#/components/schemas/LoginPageContentUpdateRequest'
        schoolAdminEditable:
          type: boolean
        theme:
          $ref: '#/components/schemas/StudentThemeSettingUpdateRequest'
        adminHome:
          $ref: '#/components/schemas/AdminHomeContentUpdateRequest'
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: Token
''')

p = 'backend-java/model/src/main/resources/openapi-interface.yaml'
replace(p,
'''  /api/v1/public/site-config:
    $ref: 'admin/openapi-site-metadata.yaml#/paths/~1api~1v1~1public~1site-config'
''',
'''  /api/v1/public/site-config:
    $ref: 'admin/openapi-site-metadata.yaml#/paths/~1api~1v1~1public~1site-config'
  /api/v1/public/site-assets/{slot}:
    $ref: 'admin/openapi-site-metadata.yaml#/paths/~1api~1v1~1public~1site-assets~1{slot}'
''')
replace(p,
'''  /api/v1/admin/settings/login-page:
    $ref: 'admin/openapi-site-metadata.yaml#/paths/~1api~1v1~1admin~1settings~1login-page'
''',
'''  /api/v1/admin/settings/login-page:
    $ref: 'admin/openapi-site-metadata.yaml#/paths/~1api~1v1~1admin~1settings~1login-page'
  /api/v1/admin/settings/site-assets/{slot}:
    $ref: 'admin/openapi-site-metadata.yaml#/paths/~1api~1v1~1admin~1settings~1site-assets~1{slot}'
''')
replace(p,
'''  /api/v1/platform/site-metadata:
    $ref: 'admin/openapi-site-metadata.yaml#/paths/~1api~1v1~1platform~1site-metadata'
''',
'''  /api/v1/platform/site-metadata:
    $ref: 'admin/openapi-site-metadata.yaml#/paths/~1api~1v1~1platform~1site-metadata'
  /api/v1/platform/site-metadata/assets/{slot}:
    $ref: 'admin/openapi-site-metadata.yaml#/paths/~1api~1v1~1platform~1site-metadata~1assets~1{slot}'
''')

write('backend-java/server/src/main/java/com/wust/dormitory/admin/SiteMetadataAssetController.java', '''package com.wust.dormitory.admin;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.api.SiteMetadataAssetApi;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@RestController
public class SiteMetadataAssetController implements SiteMetadataAssetApi {
    private final SiteMetadataAssetService service;

    public SiteMetadataAssetController(SiteMetadataAssetService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> uploadPlatformSiteAsset(String slot, MultipartFile file) {
        CurrentUser user = SecurityUsers.requirePlatformOperation();
        return ResponseEntity.ok(ResponseFactory.object(service.upload(user, slot, file)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> uploadAdminSiteAsset(String slot, MultipartFile file) {
        CurrentUser user = SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.upload(user, slot, file)));
    }

    @Override
    public ResponseEntity<Resource> getPublicSiteAsset(String slot) {
        SiteMetadataAssetService.AssetRead read = service.open(slot);
        InputStreamResource resource = new InputStreamResource(read.result().inputStream());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(read.contentType()))
                .contentLength(read.contentLength())
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(resource);
    }
}
''')

print('site asset OpenAPI closure applied')
