
package Geral;
import java.io.File;
import java.io.FileInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;
import static java.lang.Thread.sleep;
 
public class Cliente {
 
    static final int CABECALHO = 4;
    static final int TAMANHO_PACOTE = 1000;      static final int TAMANHO_JANELA = 10;
    static final int VALOR_TEMPORIZADOR = 1000;
    static final int PORTA_SERVIDOR = 8000;
    static final int PORTA_ACK = 9000;
 
    int base;   
    int proxNum;   
    String caminho;     
    List<byte[]> listaPacotes;
    Timer tempo;
    Semaphore semaforo;
    boolean transferenciaCompleta;
 
    
    public Cliente(int portaDestino, int portaEntrada, String caminho, String enderecoIp) {
        base = 0;
        proxNum = 0;
        this.caminho = caminho;
        listaPacotes = new ArrayList<>(TAMANHO_JANELA);
        transferenciaCompleta = false;
        DatagramSocket socketSaida, socketEntrada;
        semaforo = new Semaphore(1);
        System.out.println("Cliente: porta de destino: " + portaDestino + ", porta de entrada: " + portaEntrada + ", caminho: " + caminho);
 
        try {
            
            socketSaida = new DatagramSocket();
            socketEntrada = new DatagramSocket(portaEntrada);
 
            
            ThreadEntrada tEntrada = new ThreadEntrada(socketEntrada);
            ThreadSaida tSaida = new ThreadSaida(socketSaida, portaDestino, portaEntrada, enderecoIp);
            tEntrada.start();
            tSaida.start();
 
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
  
 
    public class Temporizador extends TimerTask {
 
        public void run() {
            try {
                semaforo.acquire();
                System.out.println("Cliente: Tempo expirado!");
                proxNum = base;  
                semaforo.release();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
 
    
    public void manipularTemporizador(boolean novoTimer) {
        if (tempo != null) {
            tempo.cancel();
        }
        if (novoTimer) {
            tempo = new Timer();
            tempo.schedule(new Temporizador(), VALOR_TEMPORIZADOR);
        }
    }
 
    public class ThreadSaida extends Thread {
 
        private DatagramSocket socketSaida;
        private int portaDestino;
        private InetAddress enderecoIP;
        private int portaEntrada;
 
        
        public ThreadSaida(DatagramSocket socketSaida, int portaDestino, int portaEntrada, String enderecoIP) throws UnknownHostException {
            this.socketSaida = socketSaida;
            this.portaDestino = portaDestino;
            this.portaEntrada = portaEntrada;
            this.enderecoIP = InetAddress.getByName(enderecoIP);
        }
 
        
        public byte[] gerarPacote(int numSeq, byte[] dadosByte) {
            byte[] numSeqByte = ByteBuffer.allocate(CABECALHO).putInt(numSeq).array();
            ByteBuffer bufferPacote = ByteBuffer.allocate(CABECALHO + dadosByte.length);
            bufferPacote.put(numSeqByte);
            bufferPacote.put(dadosByte);
            return bufferPacote.array();
        }
 
        public void run() {
            try {
                FileInputStream fis = new FileInputStream(new File(caminho));
 
                try {
                    while (!transferenciaCompleta) {    
                        if (proxNum < base + (TAMANHO_JANELA * TAMANHO_PACOTE)) {
                            semaforo.acquire();
                            if (base == proxNum) {   
                                manipularTemporizador(true);
                            }
                            byte[] enviaDados = new byte[CABECALHO];
                            boolean ultimoNumSeq = false;
 
                            if (proxNum < listaPacotes.size()) {
                                enviaDados = listaPacotes.get(proxNum);
                            } else {
                                byte[] dataBuffer = new byte[TAMANHO_PACOTE];
                                int tamanhoDados = fis.read(dataBuffer, 0, TAMANHO_PACOTE);
                                if (tamanhoDados == -1) {   
                                    ultimoNumSeq = true;
                                    enviaDados = gerarPacote(proxNum, new byte[0]);
                                } else {    
                                    byte[] dataBytes = Arrays.copyOfRange(dataBuffer, 0, tamanhoDados);
                                    enviaDados = gerarPacote(proxNum, dataBytes);
                                }
                                listaPacotes.add(enviaDados);
                            }
                           
                            socketSaida.send(new DatagramPacket(enviaDados, enviaDados.length, enderecoIP, portaDestino));
                            System.out.println("Cliente: Numero de sequencia enviado " + proxNum);
 
                           
                            if (!ultimoNumSeq) {
                                proxNum += TAMANHO_PACOTE;
                            }
                            semaforo.release();
                        }
                        sleep(5);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    manipularTemporizador(false);
                    socketSaida.close();
                    fis.close();
                    System.out.println("Cliente: Socket de saida fechado!");
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }
    }
 
    public class ThreadEntrada extends Thread {
 
        private DatagramSocket socketEntrada;
 
        public ThreadEntrada(DatagramSocket socketEntrada) {
            this.socketEntrada = socketEntrada;
        }
 
        
        int getnumAck(byte[] pacote) {
            byte[] numAckBytes = Arrays.copyOfRange(pacote, 0, CABECALHO);
            return ByteBuffer.wrap(numAckBytes).getInt();
        }
 
        public void run() {
            try {
                byte[] recebeDados = new byte[CABECALHO];  
                DatagramPacket recebePacote = new DatagramPacket(recebeDados, recebeDados.length);
                try {
                    while (!transferenciaCompleta) {
                        socketEntrada.receive(recebePacote);
                        int numAck = getnumAck(recebeDados);
                        System.out.println("Cliente: Ack recebido " + numAck);
                        
                        if (base == numAck + TAMANHO_PACOTE) {
                            semaforo.acquire();
                            manipularTemporizador(false);
                            proxNum = base;
                            semaforo.release();
                        } else if (numAck == -2) {
                            transferenciaCompleta = true;
                        } //ACK normal
                        else {
                            base = numAck + TAMANHO_PACOTE;
                            semaforo.acquire();
                            if (base == proxNum) {
                                manipularTemporizador(false);
                            } else {
                                manipularTemporizador(true);
                            }
                            semaforo.release();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    socketEntrada.close();
                    System.out.println("Cliente: Socket fechado!");
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }
    }
 
    public static void main(String[] args) {
        Scanner teclado = new Scanner(System.in);
        
        System.out.print("Digite o endereco do servidor: ");
        String enderecoIP = teclado.nextLine();
        System.out.print("Digite o diretorio do arquivo a ser enviado: ");
        String diretorio = teclado.nextLine();
        System.out.print("Digite o nome do arquivo a ser enviado: ");
        String nome = teclado.nextLine();
 
        Cliente cliente = new Cliente(PORTA_SERVIDOR, PORTA_ACK, diretorio + nome, enderecoIP);
    }
}
