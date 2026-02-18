<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<yb_canvas uuid="d4e5f6a7-890b-1234-c5d6-e7f890a1b2c3" mversion="30" type="code" subtype="single" lang="jsp">
    <title>首页文件</title>
    <filename>index.jsp</filename>
    <!DOCTYPE html>
    <html lang="zh-CN">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>体育选课系统 - 首页</title>
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
        <style>
            body {
                font-family: 'Segoe UI', 'Microsoft YaHei', sans-serif;
                margin: 0;
                padding: 0;
                background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                min-height: 100vh;
                display: flex;
                justify-content: center;
                align-items: center;
            }

            .container {
                max-width: 800px;
                width: 90%;
                background: white;
                border-radius: 20px;
                box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
                overflow: hidden;
            }

            .header {
                background: linear-gradient(135deg, #1a73e8 0%, #0d62d9 100%);
                color: white;
                padding: 40px;
                text-align: center;
            }

            .header h1 {
                font-size: 2.8em;
                margin: 0 0 15px 0;
                display: flex;
                align-items: center;
                justify-content: center;
                gap: 20px;
            }

            .header p {
                font-size: 1.2em;
                opacity: 0.9;
                margin: 0;
            }

            .content {
                padding: 40px;
            }

            .features {
                display: grid;
                grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
                gap: 25px;
                margin: 30px 0;
            }

            .feature {
                background: #f8f9fa;
                padding: 25px;
                border-radius: 12px;
                border-left: 4px solid #1a73e8;
                transition: transform 0.3s, box-shadow 0.3s;
            }

            .feature:hover {
                transform: translateY(-5px);
                box-shadow: 0 10px 25px rgba(0, 0, 0, 0.1);
            }

            .feature i {
                font-size: 2.5em;
                color: #1a73e8;
                margin-bottom: 15px;
            }

            .feature h3 {
                margin: 0 0 10px 0;
                color: #333;
            }

            .feature p {
                margin: 0;
                color: #666;
                line-height: 1.6;
            }

            .cta-button {
                display: block;
                background: linear-gradient(135deg, #34a853 0%, #2d9249 100%);
                color: white;
                text-align: center;
                padding: 18px 40px;
                border-radius: 12px;
                text-decoration: none;
                font-size: 1.2em;
                font-weight: bold;
                margin: 40px auto 20px;
                width: fit-content;
                transition: all 0.3s;
                box-shadow: 0 5px 15px rgba(52, 168, 83, 0.3);
            }

            .cta-button:hover {
                transform: translateY(-3px);
                box-shadow: 0 8px 25px rgba(52, 168, 83, 0.4);
            }

            .stats {
                display: flex;
                justify-content: space-around;
                background: #e8f0fe;
                padding: 20px;
                border-radius: 12px;
                margin: 30px 0;
            }

            .stat-item {
                text-align: center;
            }

            .stat-number {
                font-size: 2em;
                font-weight: bold;
                color: #1a73e8;
                display: block;
            }

            .stat-label {
                color: #5f6368;
                font-size: 0.9em;
            }

            .footer {
                text-align: center;
                padding: 20px;
                color: #666;
                border-top: 1px solid #eee;
                font-size: 0.9em;
            }

            @media (max-width: 768px) {
                .container {
                    margin: 20px;
                    width: calc(100% - 40px);
                }

                .header h1 {
                    font-size: 2.2em;
                    flex-direction: column;
                    gap: 10px;
                }

                .content {
                    padding: 25px;
                }

                .stats {
                    flex-direction: column;
                    gap: 15px;
                }
            }
        </style>
    </head>
    <body>
    <div class="container">
        <div class="header">
            <h1>
                高中体育选课系统
            </h1>
            请开启您的体育之旅，选择心仪的课程
        </div>
        <div class="content">
            <h2 style="text-align: center; color: #333; margin-bottom: 20px;">欢迎使用体育选课系统</h2>
            <p style="text-align: center; color: #666; line-height: 1.6; max-width: 600px; margin: 0 auto 30px;">
                这是一个为高中生设计的在线体育课程选课平台。在这里，您可以浏览各类体育课程，
                选择感兴趣的课程，实时查看课程剩余名额，并管理自己的选课计划。
            </p>

            <div class="features">
                <div class="feature">
                    <i class="fas fa-book-open"></i>
                    <h3>课程丰富多样</h3>
                    <p>篮球、足球、羽毛球、游泳、武术等多种课程供您选择，满足不同兴趣需求。</p>
                </div>

                <div class="feature">
                    <i class="fas fa-user-check"></i>
                    <h3>选课灵活便捷</h3>
                    <p>在线实时选课，每人最多可选2门课程，支持随时查看和调整选课计划。</p>
                </div>

                <div class="feature">
                    <i class="fas fa-chart-line"></i>
                    <h3>名额实时更新</h3>
                    <p>课程名额实时显示，热门课程及时标记，帮助您做出最佳选择。</p>
                </div>

                <div class="feature">
                    <i class="fas fa-mobile-alt"></i>
                    <h3>响应式设计</h3>
                    <p>完美适配电脑、平板和手机，随时随地管理您的选课计划。</p>
                </div>
            </div>

            <div class="stats">
                <div class="stat-item">
                    <span class="stat-number">8+</span>
                    <span class="stat-label">体育课程</span>
                </div>
                <div class="stat-item">
                    <span class="stat-number">200+</span>
                    <span class="stat-label">可选名额</span>
                </div>
                <div class="stat-item">
                    <span class="stat-number">2</span>
                    <span class="stat-label">最大选课数</span>
                </div>
                <div class="stat-item">
                    <span class="stat-number">24/7</span>
                    <span class="stat-label">全天服务</span>
                </div>
            </div>

            <a href="courses" class="cta-button">
                <i class="fas fa-arrow-right"></i> 开始选课
            </a>
        </div>

        <div class="footer">
            <p>© 2026 高级中学体育部 · 版本 1.0.0</p>
            <p>技术支持：信息技术部门 | 联系电话</p>
        </div>
    </div>
    </body>
    </html>
</yb_canvas>