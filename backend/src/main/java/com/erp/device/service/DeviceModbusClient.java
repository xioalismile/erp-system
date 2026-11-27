package com.erp.device.service;

import com.erp.device.entity.DevicePoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 最小 Modbus TCP 主站（阶段 3）：
 * - 支持功能码 03（保持寄存器）与 01（线圈），每次读 1 个；
 * - 地址写法：HR:100 / 100（保持寄存器）、COIL:0（线圈）；
 * - 只支持 16 位寄存器；32 位浮点请用两个点位加公式换算，或等待后续版本。
 */
@Slf4j
@Component
public class DeviceModbusClient {

    private final AtomicInteger transaction = new AtomicInteger(1);

    public Map<String, Object> readAll(String host, int port, int unitId,
                                       List<DevicePoint> points, int timeoutMs) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (points == null || points.isEmpty()) return values;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();
            for (DevicePoint p : points) {
                try {
                    String addr = p.getAddress() == null ? "" : p.getAddress().trim();
                    if (addr.isEmpty()) continue;
                    if (addr.toUpperCase().startsWith("COIL:")) {
                        int a = Integer.parseInt(addr.substring(5).trim());
                        values.put(p.getPointKey(), readCoil(in, out, unitId, a));
                    } else {
                        String reg = addr.toUpperCase().startsWith("HR:") ? addr.substring(3).trim() : addr;
                        int a = Integer.parseInt(reg);
                        values.put(p.getPointKey(), readHolding(in, out, unitId, a));
                    }
                } catch (Exception e) {
                    log.warn("[Modbus] 设备点位 {} 读取失败: {}", p.getPointKey(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[Modbus] 连接 {}:{} 失败: {}", host, port, e.getMessage());
        }
        return values;
    }

    private int readHolding(DataInputStream in, OutputStream out, int unitId, int address) throws IOException {
        int tx = transaction.getAndIncrement() & 0xFFFF;
        byte[] req = new byte[]{
                (byte) (tx >> 8), (byte) tx, 0, 0, 0, 6, (byte) unitId, 3,
                (byte) (address >> 8), (byte) address, 0, 1
        };
        out.write(req);
        out.flush();
        byte[] head = new byte[9];
        in.readFully(head);
        checkResponse(head, tx, unitId, 3);
        int byteCount = head[8] & 0xFF;
        byte[] data = new byte[byteCount];
        in.readFully(data);
        if (byteCount < 2) throw new IOException("寄存器返回字节不足");
        return ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
    }

    private int readCoil(DataInputStream in, OutputStream out, int unitId, int address) throws IOException {
        int tx = transaction.getAndIncrement() & 0xFFFF;
        byte[] req = new byte[]{
                (byte) (tx >> 8), (byte) tx, 0, 0, 0, 6, (byte) unitId, 1,
                (byte) (address >> 8), (byte) address, 0, 1
        };
        out.write(req);
        out.flush();
        byte[] head = new byte[9];
        in.readFully(head);
        checkResponse(head, tx, unitId, 1);
        int byteCount = head[8] & 0xFF;
        byte[] data = new byte[byteCount];
        in.readFully(data);
        return (data.length > 0 && (data[0] & 0x01) == 1) ? 1 : 0;
    }

    private void checkResponse(byte[] head, int tx, int unitId, int function) throws IOException {
        int respTx = ((head[0] & 0xFF) << 8) | (head[1] & 0xFF);
        int protocol = ((head[2] & 0xFF) << 8) | (head[3] & 0xFF);
        int respUnit = head[6] & 0xFF;
        int respFunc = head[7] & 0xFF;
        if (respTx != tx || protocol != 0 || respUnit != unitId) {
            throw new IOException("Modbus 响应头不匹配");
        }
        if ((respFunc & 0x80) != 0) {
            throw new IOException("Modbus 异常码 " + (head[8] & 0xFF));
        }
        if (respFunc != function) {
            throw new IOException("Modbus 功能码不匹配");
        }
    }

    /** 写单个保持寄存器（功能码 06） */
    public boolean writeRegister(String host, int port, int unitId, int address, int value, int timeoutMs) {
        int tx = transaction.getAndIncrement() & 0xFFFF;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();
            byte[] req = new byte[]{
                    (byte) (tx >> 8), (byte) tx, 0, 0, 0, 6, (byte) unitId, 6,
                    (byte) (address >> 8), (byte) address, (byte) (value >> 8), (byte) value
            };
            out.write(req);
            out.flush();
            byte[] resp = new byte[12];
            in.readFully(resp);
            checkResponse(resp, tx, unitId, 6);
            int respAddr = ((resp[8] & 0xFF) << 8) | (resp[9] & 0xFF);
            int respVal = ((resp[10] & 0xFF) << 8) | (resp[11] & 0xFF);
            return respAddr == address && respVal == (value & 0xFFFF);
        } catch (Exception e) {
            log.warn("[Modbus] 写寄存器 {} 失败: {}", address, e.getMessage());
            return false;
        }
    }
}
